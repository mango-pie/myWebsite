package com.ai.config;

import com.ai.model.vo.ModuleCapabilitiesVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ModuleEnablementService {

    @Resource
    private AppModuleProperties appModuleProperties;

    public boolean isEnabled(String moduleName) {
        return appModuleProperties.isEnabled(moduleName);
    }

    public Map<String, Boolean> snapshot() {
        Map<String, Boolean> map = new LinkedHashMap<>();
        map.put("ops", appModuleProperties.isOps());
        map.put("blog", appModuleProperties.isBlog());
        map.put("knowledge", appModuleProperties.isKnowledge());
        map.put("reading", appModuleProperties.isReading());
        map.put("chat", appModuleProperties.isChat());
        map.put("study", appModuleProperties.isStudy());
        map.put("diary", appModuleProperties.isDiary());
        map.put("tts", appModuleProperties.isTts());
        map.put("app-lab", appModuleProperties.isAppLab());
        return map;
    }

    public ModuleCapabilitiesVO toVo() {
        ModuleCapabilitiesVO vo = new ModuleCapabilitiesVO();
        vo.setModules(snapshot());
        return vo;
    }
}
