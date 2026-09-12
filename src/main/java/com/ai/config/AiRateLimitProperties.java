package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 端点限流配置：对调用外部 LLM/抓取的高成本端点做每用户/IP 固定窗口计数。
 * 个人站量级不引入 Redis/网关，内存计数即可；规则按声明顺序取第一条命中的。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai-rate-limit")
public class AiRateLimitProperties {

    /** 总开关；关闭后所有规则失效 */
    private boolean enabled = true;

    /** 限流规则：仅命中 pattern 的请求计数，未列出的端点不限 */
    private List<Rule> rules = new ArrayList<>();

    @Data
    public static class Rule {

        /** Ant 风格路径（context path 之外），如 /kb/chat/** */
        private String pattern;

        /** 窗口内允许的最大请求数（按登录用户，未登录按来源 IP） */
        private int limit = 30;

        /** 窗口长度（秒） */
        private long windowSeconds = 60;
    }
}
