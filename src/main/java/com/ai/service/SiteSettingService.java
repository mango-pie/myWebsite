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
}
