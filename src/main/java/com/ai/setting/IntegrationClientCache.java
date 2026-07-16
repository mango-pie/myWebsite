package com.ai.setting;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 集成配置变更时递增版本号，供各客户端缓存失效。
 */
@Component
public class IntegrationClientCache {

    private final AtomicLong version = new AtomicLong(1);

    public long version() {
        return version.get();
    }

    public void invalidate() {
        version.incrementAndGet();
    }
}
