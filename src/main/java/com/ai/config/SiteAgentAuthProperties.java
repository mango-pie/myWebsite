package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 本机 Cursor / 脚本调用站点 API 的长期令牌。未配置则过滤器空转。
 * Bean 名避开 chat 模块里字段名为 agentProperties 的 ChatAgentProperties。
 */
@Data
@Component("siteAgentAuthProperties")
@ConfigurationProperties(prefix = "app.agent")
public class SiteAgentAuthProperties {

    /** 明文令牌，仅放环境变量，勿写入仓库 */
    private String token = "";

    /** 令牌对应的用户 id（建议管理员） */
    private String userId = "";

    /**
     * 令牌仅对这些路径前缀生效（逗号分隔，如 /api/library,/chat）。留空表示全站生效。
     * 用 String 承载以兼容空环境变量直绑，避免空串到集合的绑定歧义。
     */
    private String pathPrefixes = "";

    public boolean enabled() {
        return StringUtils.hasText(token) && StringUtils.hasText(userId);
    }

    /**
     * 令牌是否允许用于该请求路径。未配置前缀时全放行。
     */
    public boolean allowsPath(String requestUri) {
        String[] prefixes = StringUtils.commaDelimitedListToStringArray(pathPrefixes);
        if (prefixes.length == 0) {
            return true;
        }
        for (String prefix : prefixes) {
            if (StringUtils.hasText(prefix) && requestUri.startsWith(prefix.trim())) {
                return true;
            }
        }
        return false;
    }

    public Long parsedUserId() {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
