package com.ai.setting;

import cn.hutool.json.JSONUtil;
import com.ai.constant.SiteSettingConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 模块级设置缓存；Redis 不可用时静默回落。
 */
@Slf4j
@Component
public class SiteSettingCache {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.redis.available:false}")
    private boolean redisAvailable;

    private boolean useRedis() {
        return redisAvailable && stringRedisTemplate != null;
    }

    public Map<String, String> getModuleRaw(String module, Supplier<Map<String, String>> loader) {
        if (!useRedis()) {
            return loader.get();
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
