package com.ai.setting;

import cn.hutool.json.JSONUtil;
import com.ai.constant.SiteSettingConstant;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 模块级设置缓存；Redis 不可用时静默回落。
 * Redis 关闭（如 2G 生产机）时走进程内 Caffeine 短 TTL 缓存——设置读取在每请求
 * 热路径（维护模式拦截器/登录/模块开关），无本地缓存则每次读库。
 * 写路径统一经 evict(module) 失效两级缓存；本地 TTL 取短值兜底跨实例写。
 */
@Slf4j
@Component
public class SiteSettingCache {

    /** 本地缓存 TTL：远短于 Redis 路径的 300s，兜底无通知的跨实例写 */
    private static final long LOCAL_TTL_SECONDS = 60L;
    private static final int LOCAL_MAX_MODULES = 128;

    private final Cache<String, Map<String, String>> localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(LOCAL_TTL_SECONDS))
            .maximumSize(LOCAL_MAX_MODULES)
            .build();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.redis.available:false}")
    private boolean redisAvailable;

    private boolean useRedis() {
        return redisAvailable && stringRedisTemplate != null;
    }

    public Map<String, String> getModuleRaw(String module, Supplier<Map<String, String>> loader) {
        if (!useRedis()) {
            Map<String, String> cached = localCache.getIfPresent(module);
            if (cached != null) {
                return cached;
            }
            Map<String, String> loaded = loader.get();
            if (loaded != null) {
                localCache.put(module, loaded);
            }
            return loaded;
        }
        try {
            String key = cacheKey(module);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = (Map<String, String>) (Map<?, ?>) JSONUtil.parseObj(cached);
                return parsed;
            }
            Map<String, String> loaded = loader.get();
            if (loaded != null) {
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(loaded),
                        SiteSettingConstant.CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            }
            return loaded;
        } catch (Exception e) {
            log.debug("SiteSettingCache get fallback: {}", e.getMessage());
            return loader.get();
        }
    }

    public void evict(String module) {
        localCache.invalidate(module);
        if (!useRedis()) {
            return;
        }
        try {
            stringRedisTemplate.delete(cacheKey(module));
        } catch (Exception e) {
            log.debug("SiteSettingCache evict fallback: {}", e.getMessage());
        }
    }

    private String cacheKey(String module) {
        return SiteSettingConstant.CACHE_KEY_PREFIX + module;
    }
}

