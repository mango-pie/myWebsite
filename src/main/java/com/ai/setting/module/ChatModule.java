package com.ai.setting.module;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.config.ChatAttachmentProperties;
import com.ai.config.ChatImageCaptionProperties;
import com.ai.config.ChatSegmentationProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.service.ChatAttachmentService;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChatModule implements SettingModule {

    @Resource
    private ChatAgentProperties chatAgentProperties;
    @Resource
    private ChatAttachmentProperties chatAttachmentProperties;
    @Resource
    private ChatImageCaptionProperties chatImageCaptionProperties;
    @Resource
    private ChatSegmentationProperties chatSegmentationProperties;
    @Lazy
    @Resource
    private ChatAttachmentService chatAttachmentService;

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_CHAT;
    }

    @Override
    public String displayName() {
        return "角色聊天";
    }

    @Override
    public String phase() {
        return "P2";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                bool("agent.enabled", "启用 Agent", chatAgentProperties.isEnabled(), true),
                intField("agent.max_steps", "Agent 最大步数", chatAgentProperties.getMaxSteps(), 1, 50),
                intField("agent.history_limit", "历史条数上限", chatAgentProperties.getHistoryLimit(), 1, 200),
                bool("agent.l2_enabled", "启用 L2 工具", chatAgentProperties.isL2Enabled(), false),

                intField("attachment_cache_ttl_minutes", "附件缓存 TTL(分钟)",
                        chatAttachmentProperties.getAttachmentCacheTtlMinutes(), 1, 10080),

                bool("image_caption.enabled", "启用图片转述", chatImageCaptionProperties.isEnabled(), false),
                str("image_caption.model_name", "转述模型名", chatImageCaptionProperties.getModelName()),
                text("image_caption.prompt", "转述 Prompt", chatImageCaptionProperties.getPrompt()),
                intField("image_caption.timeout_seconds", "转述超时(秒)",
                        chatImageCaptionProperties.getTimeoutSeconds(), 5, 300),

                bool("segmentation.enabled", "启用朗读分段", chatSegmentationProperties.isEnabled(), false),
                str("segmentation.style", "分段风格", chatSegmentationProperties.getStyle()),
                intField("segmentation.min_length", "最短段长", chatSegmentationProperties.getMinLength(), 1, 5000),
                intField("segmentation.max_segments", "最大段数", chatSegmentationProperties.getMaxSegments(), 1, 50),
                dbl("segmentation.temperature", "分段温度", chatSegmentationProperties.getTemperature(), 0, 2),
                intField("segmentation.max_tokens", "分段 maxTokens", chatSegmentationProperties.getMaxTokens(), 64, 8192),
                dbl("segmentation.timeout_seconds", "分段超时(秒)", chatSegmentationProperties.getTimeoutSeconds(), 1, 120),
                dbl("segmentation.delay_base", "段间延迟基数", chatSegmentationProperties.getDelayBase(), 0, 10),
                dbl("segmentation.delay_per_char", "每字延迟", chatSegmentationProperties.getDelayPerChar(), 0, 1),
                dbl("segmentation.delay_max", "延迟上限", chatSegmentationProperties.getDelayMax(), 0, 30),
                bool("segmentation.fallback_to_rules", "失败回落规则分段",
                        chatSegmentationProperties.isFallbackToRules(), false)
        );
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field.getDefaultValue());
        }
        return map;
    }

    @Override
    public void validate(Map<String, Object> items) {
        // 范围已在 schema min/max；额外校验风格非空
        if (items.containsKey("segmentation.style")) {
            Object style = items.get("segmentation.style");
            if (style == null || String.valueOf(style).isBlank()) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "segmentation.style 不能为空");
            }
        }
        if (items.containsKey("image_caption.prompt")) {
            Object prompt = items.get("image_caption.prompt");
            if (prompt != null && String.valueOf(prompt).length() > 8000) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "image_caption.prompt 过长");
            }
        }
    }

    @Override
    public void onChanged() {
        chatAttachmentService.rebuildCaptionCache();
    }

    private SettingFieldSchema bool(String key, String label, boolean def, boolean danger) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_BOOL).label(label).description(label)
                .defaultValue(def).danger(danger).build();
    }

    private SettingFieldSchema intField(String key, String label, int def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_INT).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }

    private SettingFieldSchema dbl(String key, String label, double def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_DOUBLE).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }

    private SettingFieldSchema str(String key, String label, String def) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def == null ? "" : def).build();
    }

    private SettingFieldSchema text(String key, String label, String def) {
        return str(key, label, def);
    }
}
