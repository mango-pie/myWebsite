package com.ai.setting;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发现并索引全部 SettingModule。
 */
@Component
public class SettingModuleRegistry {

    private final List<SettingModule> modules;
    private final Map<String, SettingModule> byCode = new LinkedHashMap<>();

    public SettingModuleRegistry(List<SettingModule> modules) {
        this.modules = modules;
    }

    @PostConstruct
    public void init() {
        for (SettingModule module : modules) {
            if (byCode.containsKey(module.code())) {
                throw new IllegalStateException("Duplicate SettingModule code: " + module.code());
            }
            byCode.put(module.code(), module);
        }
    }

    public SettingModule require(String code) {
        SettingModule module = byCode.get(code);
        if (module == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知设置模块: " + code);
        }
        return module;
    }

    public Collection<SettingModule> all() {
        return byCode.values();
    }

    public boolean contains(String code) {
        return byCode.containsKey(code);
    }
}
