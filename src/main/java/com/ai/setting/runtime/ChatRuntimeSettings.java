package com.ai.setting.runtime;

import com.ai.config.ConditionalOnModule;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.config.ChatAttachmentProperties;
import com.ai.config.ChatImageCaptionProperties;
import com.ai.config.ChatSegmentationProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * chat 模块运行时读取：DB 覆盖 YAML Properties。
 */
@ConditionalOnModule("chat")
@Component
public class ChatRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;
    @Resource
    private ChatAgentProperties agentProperties;
    @Resource
    private ChatAttachmentProperties attachmentProperties;
    @Resource
    private ChatImageCaptionProperties captionProperties;
    @Resource
    private ChatSegmentationProperties segmentationProperties;

    public boolean agentEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_CHAT, "agent.enabled", agentProperties.isEnabled());
    }

    public int agentMaxSteps() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "agent.max_steps", agentProperties.getMaxSteps());
    }

    public int agentHistoryLimit() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "agent.history_limit", agentProperties.getHistoryLimit());
    }

    public boolean agentL2Enabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_CHAT, "agent.l2_enabled", agentProperties.isL2Enabled());
    }

    public int attachmentCacheTtlMinutes() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "attachment_cache_ttl_minutes",
                attachmentProperties.getAttachmentCacheTtlMinutes());
    }

    public boolean imageCaptionEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_CHAT, "image_caption.enabled",
                captionProperties.isEnabled());
    }

    public String imageCaptionModelName() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_CHAT, "image_caption.model_name",
                captionProperties.getModelName());
    }

    public String imageCaptionPrompt() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_CHAT, "image_caption.prompt",
                captionProperties.getPrompt());
    }

    public int imageCaptionTimeoutSeconds() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "image_caption.timeout_seconds",
                captionProperties.getTimeoutSeconds());
    }

    public boolean segmentationEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_CHAT, "segmentation.enabled",
                segmentationProperties.isEnabled());
    }

    public String segmentationStyle() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_CHAT, "segmentation.style",
                segmentationProperties.getStyle());
    }

    public int segmentationMinLength() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "segmentation.min_length",
                segmentationProperties.getMinLength());
    }

    public int segmentationMaxSegments() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "segmentation.max_segments",
                segmentationProperties.getMaxSegments());
    }

    public double segmentationTemperature() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_CHAT, "segmentation.temperature",
                segmentationProperties.getTemperature());
    }

    public int segmentationMaxTokens() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_CHAT, "segmentation.max_tokens",
                segmentationProperties.getMaxTokens());
    }

    public double segmentationTimeoutSeconds() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_CHAT, "segmentation.timeout_seconds",
                segmentationProperties.getTimeoutSeconds());
    }

    public double segmentationDelayBase() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_CHAT, "segmentation.delay_base",
                segmentationProperties.getDelayBase());
    }

    public double segmentationDelayPerChar() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_CHAT, "segmentation.delay_per_char",
                segmentationProperties.getDelayPerChar());
    }

    public double segmentationDelayMax() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_CHAT, "segmentation.delay_max",
                segmentationProperties.getDelayMax());
    }

    public boolean segmentationFallbackToRules() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_CHAT, "segmentation.fallback_to_rules",
                segmentationProperties.isFallbackToRules());
    }
}
