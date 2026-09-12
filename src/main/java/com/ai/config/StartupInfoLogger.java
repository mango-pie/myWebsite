package com.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动自检：应用就绪后打印关键运行配置，便于部署/排障快速确认「实际加载了什么」。
 * 不打印任何密钥。仅做只读汇总与风险提示，不改变启动流程。
 */
@Component
public class StartupInfoLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupInfoLogger.class);

    private final Environment env;
    private final AppModuleProperties modules;

    public StartupInfoLogger(Environment env, AppModuleProperties modules) {
        this.env = env;
        this.modules = modules;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logStartupSummary() {
        String profiles = String.join(",", env.getActiveProfiles());
        if (profiles.isBlank()) {
            profiles = "(default)";
        }
        String dbUrl = env.getProperty("spring.datasource.url", "(none)");
        String redisHost = env.getProperty("spring.data.redis.host", "127.0.0.1");
        String redisPort = env.getProperty("spring.data.redis.port", "6379");
        String redisAvailable = env.getProperty("app.redis.available", "unknown");
        String sessionStore = env.getProperty("spring.session.store-type", "none");
        String flyway = env.getProperty("spring.flyway.enabled", "true");
        String serverPort = env.getProperty("server.port", "8123");
        String contextPath = env.getProperty("server.servlet.context-path", "");

        log.info("==================== 启动配置自检 ====================");
        log.info("profiles      : {}", profiles);
        log.info("server        : port={} context-path={}", serverPort, contextPath);
        log.info("datasource    : {}", dbUrl);
        log.info("redis         : {}:{} available={} session-store={}",
                redisHost, redisPort, redisAvailable, sessionStore);
        log.info("flyway        : enabled={}", flyway);
        log.info("modules       : {}", moduleSummary());
        log.info("=====================================================");

        if ("auto".equalsIgnoreCase(env.getProperty("app.redis.mode", "auto"))
                && !"true".equalsIgnoreCase(redisAvailable)) {
            log.warn("[自检] Redis 不可用，已降级为内存 Session。若确实不需要 Redis，建议显式设置 APP_REDIS_MODE=off。");
        }
    }

    private String moduleSummary() {
        StringBuilder sb = new StringBuilder();
        for (String m : new String[]{"blog", "chat", "study", "diary", "worklog", "knowledge",
                "reading", "tts", "ops", "app-lab"}) {
            if (!modules.isEnabled(m)) {
                sb.append(m).append("=off ");
            }
        }
        return sb.length() == 0 ? "all enabled" : sb.toString().trim();
    }
}
