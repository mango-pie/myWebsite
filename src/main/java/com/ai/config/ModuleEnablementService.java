package com.ai.config;

import com.ai.model.vo.ModuleCapabilitiesVO;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块开关最终生效状态：站点设置 modules 的 DB 覆盖优先，缺行回落 yml / 环境变量默认值。
 * 缺行绝不能当成 false，否则上线瞬间会把全站模块清空。
 */
@Service
public class ModuleEnablementService {

    @Resource
    private AppModuleProperties appModuleProperties;

    @Resource
    @Lazy
    private SiteSettingService siteSettingService;

    public boolean isEnabled(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return false;
        }
        Map<String, Boolean> overrides = siteSettingService.moduleSwitchOverrides();
        Boolean overridden = overrides.get(AppModuleProperties.normalizeKey(moduleName));
        return overridden != null ? overridden : appModuleProperties.isEnabled(moduleName);
    }

    public Map<String, Boolean> snapshot() {
        Map<String, Boolean> overrides = siteSettingService.moduleSwitchOverrides();
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("ops", resolve("ops", overrides, appModuleProperties.isOps()));
        map.put("blog", resolve("blog", overrides, appModuleProperties.isBlog()));
        map.put("knowledge", resolve("knowledge", overrides, appModuleProperties.isKnowledge()));
        map.put("reading", resolve("reading", overrides, appModuleProperties.isReading()));
        map.put("chat", resolve("chat", overrides, appModuleProperties.isChat()));
        map.put("study", resolve("study", overrides, appModuleProperties.isStudy()));
        map.put("diary", resolve("diary", overrides, appModuleProperties.isDiary()));
        map.put("worklog", resolve("worklog", overrides, appModuleProperties.isWorklog()));
        map.put("tts", resolve("tts", overrides, appModuleProperties.isTts()));
        map.put("app-lab", resolve("app-lab", overrides, appModuleProperties.isAppLab()));
        return map;
    }

    private boolean resolve(String key, Map<String, Boolean> overrides, boolean deploymentDefault) {
        Boolean overridden = overrides.get(key);
        return overridden != null ? overridden : deploymentDefault;
    }

    public ModuleCapabilitiesVO toVo() {
        ModuleCapabilitiesVO vo = new ModuleCapabilitiesVO();
        vo.setModules(snapshot());
        return vo;
    }
}
