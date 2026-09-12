package com.ai.service;

import com.ai.model.dto.setting.SiteSettingUpdateRequest;
import com.ai.model.vo.setting.SettingModuleSchemaVO;
import com.ai.model.vo.setting.SettingModuleVO;
import com.ai.model.vo.setting.SettingModuleValuesVO;
import com.ai.model.vo.setting.SiteSettingAuditVO;
import com.ai.model.vo.setting.SiteSettingsBootstrapVO;
import com.mybatisflex.core.paginate.Page;

import java.util.List;

public interface SiteSettingService {

    List<SettingModuleVO> listModules();

    SettingModuleSchemaVO getSchema(String module);

    SettingModuleValuesVO getModuleValues(String module);

    void updateModule(String module, SiteSettingUpdateRequest request, Long operatorId);

    void resetModule(String module, Long operatorId);

    Page<SiteSettingAuditVO> pageAudit(String module, int pageNum, int pageSize);

    SiteSettingsBootstrapVO bootstrap(boolean admin);

    String getString(String module, String key, String defaultValue);

    boolean getBool(String module, String key, boolean defaultValue);

    int getInt(String module, String key, int defaultValue);

    double getDouble(String module, String key, double defaultValue);

    /**
     * 仅当 DB 存在覆盖时返回（敏感字段已解密）；无覆盖返回 empty。
     */
    java.util.Optional<String> findOverride(String module, String key);

    /**
     * 业务模块开关的 DB 覆盖值（modules 设置模块）。
     *
     * @return {@code true}/{@code false} 表示已覆盖；{@code null} 表示未覆盖（应回落 yml / 环境变量默认值）。
     *         缺行绝不能当成 false，否则会误关全站模块。
     */
    Boolean getModuleSwitch(String moduleName);

    /**
     * 一次性读取全部模块开关覆盖（避免逐 key 查库）；仅含已覆盖项，key 为 kebab-case。
     */
    java.util.Map<String, Boolean> moduleSwitchOverrides();
}
