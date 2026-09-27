package com.ai.service.library.impl;

import cn.hutool.json.JSONUtil;
import com.ai.model.vo.library.LibrarySummaryVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 资料库 summary 缓存；Redis 不可用时静默回落（模式同 com.ai.setting.SiteSettingCache）。
 * Redis 关闭（如 2G 生产机）时走进程内 Caffeine 短 TTL 缓存——summary 是前端面板热路径。
 * 缓存按用户分键，两级都存 JSON 串、读取时反序列化，避免共享可变 VO 实例。
 * 写路径统一经 evict(userId) 失效两级缓存；本地 TTL 取短值兜底跨实例写。
 * 配额校验（upload-init）不走本缓存，用即时 SQL 聚合，避免陈旧缓存导致超额。
 */
@Slf4j
@Component
public class LibrarySummaryCache {

    /** 本地缓存 TTL：远短于 Redis 路径的 300s，兜底无通知的跨实例写 */
    private static final long LOCAL_TTL_SECONDS = 60L;
    private static final int LOCAL_MAX_USERS = 1024;
    private static final long REDIS_TTL_SECONDS = 300L;
    private static final String KEY_PREFIX = "library:summary:";

    private final Cache<Long, String> localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(LOCAL_TTL_SECONDS))
            .maximumSize(LOCAL_MAX_USERS)
            .build();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.redis.available:false}")
    private boolean redisAvailable;

    private boolean useRedis() {
        return redisAvailable && stringRedisTemplate != null;
    }

    public LibrarySummaryVO get(Long userId, Supplier<LibrarySummaryVO> loader) {
        if (!useRedis()) {
            String cached = localCache.getIfPresent(userId);
            if (cached != null) {
                return JSONUtil.toBean(cached, LibrarySummaryVO.class);
            }
            LibrarySummaryVO loaded = loader.get();
            localCache.put(userId, JSONUtil.toJsonStr(loaded));
            return loaded;
        }
        try {
            String key = KEY_PREFIX + userId;
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSONUtil.toBean(cached, LibrarySummaryVO.class);
            }
            LibrarySummaryVO loaded = loader.get();
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(loaded),
                    REDIS_TTL_SECONDS, TimeUnit.SECONDS);
            return loaded;
        } catch (Exception e) {
            log.debug("LibrarySummaryCache get fallback: {}", e.getMessage());
            return loader.get();
        }
    }

    public void evict(Long userId) {
        localCache.invalidate(userId);
        if (!useRedis()) {
            return;
        }
        try {
            stringRedisTemplate.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.debug("LibrarySummaryCache evict fallback: {}", e.getMessage());
        }
    }
}
