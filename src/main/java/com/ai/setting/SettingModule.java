package com.ai.setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 设置模块注册接口。各业务模块实现后由 Registry 自动发现。
 */
public interface SettingModule {

    String code();

    String displayName();

    /** 接入阶段标记，如 P0 / P1 */
    String phase();

    default boolean writable() {
        return true;
    }

    List<SettingFieldSchema> schema();

    /**
     * 模块默认值（对齐 YAML / 文档约定）。
     */
    Map<String, Object> defaults();

    /**
     * 校验待写入项；非法时抛 BusinessException。
     */
    void validate(Map<String, Object> items);

    /**
     * 变更后回调（清业务缓存等）。
     */
    default void onChanged() {
    }

    default Map<String, SettingFieldSchema> schemaByKey() {
        Map<String, SettingFieldSchema> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field);
        }
        return map;
    }
}
