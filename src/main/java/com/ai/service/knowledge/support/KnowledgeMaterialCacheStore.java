package com.ai.service.knowledge.support;

import com.ai.constant.knowledge.LearningTreeRedisConstant;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 材料包缓存：URL 读页结果按 URL hash 缓存（默认 30 天），同 URL 再采直接复用，不重复烧模型。
 * Redis 优先，内存降级（单实例）。
 */
@Slf4j
@Component
public class KnowledgeMaterialCacheStore {

    private static final long TTL_SECONDS = LearningTreeRedisConstant.MATERIAL_TTL_SECONDS;
    private static final int MEMORY_MAX_ENTRIES = 16;

    private final Map<String, String> memoryStore = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MEMORY_MAX_ENTRIES;
                }
            });

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.redis.available:false}")
    private boolean redisAvailable;

    private boolean useRedis() {
        return redisAvailable && stringRedisTemplate != null;
    }

    /** value 为序列化后的 MaterialSource JSON */
    public String get(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String key = LearningTreeRedisConstant.materialKey(url);
        if (useRedis()) {
            try {
                String json = stringRedisTemplate.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    return json;
                }
            } catch (Exception e) {
                log.debug("Redis material get failed, try memory: {}", e.getMessage());
            }
        }
        return memoryStore.get(key);
    }

    public void put(String url, String serialized) {
        if (url == null || url.isBlank() || serialized == null || serialized.isBlank()) {
            return;
        }
        String key = LearningTreeRedisConstant.materialKey(url);
        if (useRedis()) {
            try {
                stringRedisTemplate.opsForValue().set(key, serialized, TTL_SECONDS, TimeUnit.SECONDS);
                return;
            } catch (Exception e) {
                log.debug("Redis material put failed, fallback memory: {}", e.getMessage());
            }
        }
        memoryStore.put(key, serialized);
    }

    /**
     * 强制刷新入口：清空全部材料包缓存（Redis + 内存降级）。
     * 下次同一 URL 采集会重新读页，不命中旧快照。
     *
     * @return 清理的缓存条数（含 Redis 与内存）
     */
    public int clearAll() {
        int cleared = 0;
        if (useRedis()) {
            try {
                java.util.Set<String> keys = stringRedisTemplate.keys(
                        LearningTreeRedisConstant.KEY_MATERIAL_PREFIX + "*");
                if (keys != null && !keys.isEmpty()) {
                    stringRedisTemplate.delete(keys);
                    cleared += keys.size();
                }
            } catch (Exception e) {
                log.debug("Redis material clear failed, memory still cleared: {}", e.getMessage());
            }
        }
        int mem = memoryStore.size();
        memoryStore.clear();
        return cleared + mem;
    }

    public String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Material serialize failed: {}", e.getMessage());
            return null;
        }
    }
}
