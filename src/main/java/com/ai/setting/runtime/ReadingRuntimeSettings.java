package com.ai.setting.runtime;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.SiteSettingConstant;
import com.ai.service.SiteSettingService;
import com.ai.service.knowledge.support.ReadingSkillLoader;
import com.ai.setting.module.ReadingModule;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * reading 模块运行时读取：DB 覆盖默认值。
 */
@ConditionalOnModule("knowledge")
@Component
public class ReadingRuntimeSettings {

    @Resource
    private SiteSettingService siteSettingService;

    public double distillTemperature() {
        return siteSettingService.getDouble(SiteSettingConstant.MODULE_READING, "distill.temperature", 0.2);
    }

    public int distillMaxTokens() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_READING, "distill.max_tokens", 4096);
    }

    /**
     * 蒸馏系统提示：默认用内置精读 Skill；仅当站点设置了非空且非旧默认文案时，作为附加指令拼在 Skill 后。
     */
    public String distillSystemPrompt() {
        return ReadingSkillLoader.buildSystemPrompt(null, null, distillExtraPrompt());
    }

    /** 站点「蒸馏 Prompt」：空或旧默认值视为未自定义。 */
    public String distillExtraPrompt() {
        String configured = siteSettingService.getString(
                SiteSettingConstant.MODULE_READING, "distill.system_prompt", "");
        if (StrUtil.isBlank(configured)
                || ReadingModule.DEFAULT_DISTILL_SYSTEM_PROMPT.equals(configured.trim())) {
            return "";
        }
        return configured.trim();
    }

    public boolean publishDefaultAsDraft() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "publish.default_as_draft", true);
    }

    public boolean publishAskOpenEditor() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "publish.ask_open_editor", true);
    }

    /** 版权合规：发布博客时强制在正文末尾附原文链接 */
    public boolean publishForceSourceLink() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "publish.force_source_link", true);
    }

    public boolean redistillConfirmRequired() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "redistill.confirm_required", true);
    }

    /** 余额不足熔断：开启后新任务提交前检查供应商余额 */
    public boolean balanceCheckEnabled() {
        return siteSettingService.getBool(SiteSettingConstant.MODULE_READING, "ingest.balance_check_enabled", false);
    }

    /** 排队任务上限（单用户 PENDING/QUEUED 数量） */
    public int maxQueuedJobs() {
        return siteSettingService.getInt(SiteSettingConstant.MODULE_READING, "ingest.max_queued", 5);
    }

    public String searchProvider() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_READING, "search.provider", "deepseek");
    }

    public String ingestSyncMode() {
        return siteSettingService.getString(SiteSettingConstant.MODULE_READING, "ingest.sync_mode", "async");
    }

    public boolean ingestAsync() {
        return "async".equalsIgnoreCase(ingestSyncMode());
    }
}
