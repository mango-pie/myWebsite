package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.tavily")
public class KnowledgeTavilyProperties {

    /** Tavily API Key；可用环境变量 KNOWLEDGE_TAVILY_API_KEY */
    private String apiKey = "";

    private String baseUrl = "https://api.tavily.com";

    private int maxResults = 5;

    private int timeoutSeconds = 30;

    /** 硬排除的视频等域名 */
    private List<String> excludeDomains = new ArrayList<>(List.of(
            "youtube.com",
            "youtu.be",
            "bilibili.com",
            "b23.tv",
            "vimeo.com",
            "tiktok.com",
            "douyin.com",
            "ixigua.com",
            "youku.com",
            "iqiyi.com"
    ));

    /**
     * 文章域弱偏置；非空时先带 include_domains 搜索，结果过少再降级为仅 exclude。
     */
    private List<String> includeDomains = new ArrayList<>(List.of(
            "developer.mozilla.org",
            "docs.spring.io",
            "redis.io",
            "github.com",
            "juejin.cn",
            "cnblogs.com",
            "infoq.cn",
            "stackoverflow.com",
            "medium.com",
            "dev.to"
    ));
}
