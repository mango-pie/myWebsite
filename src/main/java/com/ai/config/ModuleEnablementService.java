package com.ai.config;

import com.ai.model.vo.ModuleCapabilitiesVO;
import com.ai.service.SiteSettingService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模块开关「最终生效状态」的唯一权威出口：DB 覆盖（站点设置 modules）优先，缺行回落部署默认
 * （yml / APP_MODULES_* 环境变量）。
 *
 * <p>关键语义：<b>缺行 = 未覆盖 = 用部署默认值</b>，绝不能把「无记录」当成 false，否则上线瞬间全站模块被清空。
 *
 * <p>注意与 Bean 级门禁的分工：{@code @ConditionalOnModule} 在容器启动时从 Environment 求值，
 * 不经本类；因此本类的 DB 覆盖决定「前端菜单 / 路由 / API 门禁」的运行时状态，而 Bean 的物理装卸
 * 需要重启应用才完成。改开关后的实际生效范围见 docs/modules-setting-module.md。
 */
@Service
public class ModuleEnablementService {

    @Resource
    private AppModuleProperties appModuleProperties;

    @Resource
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
        Map<String, Boolean> map = new LinkedHashMap<>();
        // 一次取回全部覆盖，避免逐 key 查库（Cache 命中时为纯内存操作）
        Map<String, Boolean> overrides = siteSettingService.moduleSwitchOverrides();
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
