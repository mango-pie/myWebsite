package com.ai.config;

import com.ai.service.SiteSettingService;
import com.ai.setting.module.ModulesSettingModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 验收脚本：DB 覆盖优先、缺行回落部署默认（缺行绝不能当成 false）。
 */
@ExtendWith(MockitoExtension.class)
class ModuleEnablementServiceTest {

    @Mock
    private SiteSettingService siteSettingService;

    private AppModuleProperties appModuleProperties;
    private ModuleEnablementService service;

    @BeforeEach
    void setUp() {
        appModuleProperties = new AppModuleProperties();
        // 模拟 prod 部署默认：tts / app-lab 关闭，其余开启
        appModuleProperties.setTts(false);
        appModuleProperties.setAppLab(false);

        service = new ModuleEnablementService();
        ReflectionTestUtils.setField(service, "appModuleProperties", appModuleProperties);
        ReflectionTestUtils.setField(service, "siteSettingService", siteSettingService);
    }

    private void overrides(Map<String, Boolean> map) {
        when(siteSettingService.moduleSwitchOverrides()).thenReturn(map);
    }

    @Test
    void missingRow_fallsBackToDeploymentDefault_notFalse() {
        overrides(Map.of());

        assertTrue(service.isEnabled("chat"), "无覆盖的开启模块应保持开启，不能因缺行变 false");
        assertFalse(service.isEnabled("tts"), "无覆盖的关闭模块应保持部署默认 false");
        assertFalse(service.isEnabled("app-lab"));
    }

    @Test
    void dbOverride_winsOverDeploymentDefault() {
        overrides(Map.of("chat", false, "tts", true, "app-lab", true));

        assertFalse(service.isEnabled("chat"), "DB 关闭应覆盖 yml 开启");
        assertTrue(service.isEnabled("tts"), "DB 开启应覆盖 yml 关闭");
        assertTrue(service.isEnabled("app-lab"));
    }

    @Test
    void nameNormalization_underscoreAndUppercase() {
        overrides(Map.of("app-lab", true));

        assertTrue(service.isEnabled("app_lab"));
        assertTrue(service.isEnabled("APP-LAB"));
        assertFalse(service.isEnabled(""), "空模块名视为不可用");
        assertFalse(service.isEnabled(null));
    }

    @Test
    void snapshot_reflectsOverridesAndKeepsContractKeys() {
        overrides(Map.of("chat", false, "tts", true));

        Map<String, Boolean> snapshot = service.snapshot();
        assertEquals(List.of("ops", "blog", "knowledge", "reading", "chat", "study",
                        "diary", "worklog", "tts", "app-lab"),
                List.copyOf(snapshot.keySet()));
        assertFalse(snapshot.get("chat"));
        assertTrue(snapshot.get("tts"));
        assertTrue(snapshot.get("blog"));
    }

    @Test
    void modulesSettingModule_schemaKeysMatchSnapshotContract() {
        ModulesSettingModule module = new ModulesSettingModule();
        ReflectionTestUtils.setField(module, "appModuleProperties", appModuleProperties);

        List<String> schemaKeys = module.schema().stream()
                .map(f -> f.getKey())
                .toList();
        assertEquals(List.copyOf(service.snapshot().keySet()), schemaKeys);
        assertEquals("modules", module.code());
        assertTrue(module.writable());
        // 未覆盖时 schema 默认值应等于部署默认（tts 在 prod 为关）
        Map<String, Object> defaults = new LinkedHashMap<>();
        module.schema().forEach(f -> defaults.put(f.getKey(), f.getDefaultValue()));
        assertEquals(Boolean.FALSE, defaults.get("tts"));
        assertEquals(Boolean.TRUE, defaults.get("chat"));
    }
}
