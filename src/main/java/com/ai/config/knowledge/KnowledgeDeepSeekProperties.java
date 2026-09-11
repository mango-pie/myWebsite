package com.ai.config.knowledge;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "knowledge.deepseek")
public class KnowledgeDeepSeekProperties {

    /** DeepSeek 官方 API Key（与 DashScope knowledge.ai 不是同一个） */
    private String apiKey = "";

    /** Anthropic 兼容端，用于 web_search */
    private String baseUrl = "https://api.deepseek.com/anthropic";

    /** 兼容旧配置；阶段模型未配置时作为兜底 */
    private String model = "deepseek-v4-pro";

    /** 搜索候选 URL：只找页，优先低延迟 */
    private String searchModel = "deepseek-v4-flash";

    /** 勾选后读取网页材料（低成本，默认 flash） */
    private String readingModel = "deepseek-v4-flash";

    /** 基于物化材料重构精读 */
    private String distillModel = "deepseek-v4-pro";

    private int maxResults = 8;

    private int timeoutSeconds = 120;
}
