package com.ai.service;

import com.ai.model.vo.setting.SiteSettingAuditVO;
import com.mybatisflex.core.paginate.Page;

public interface SiteSettingAuditService {

    void record(String module, String settingKey, String oldValue, String newValue,
                Long operatorId, String action);

    Page<SiteSettingAuditVO> page(String module, int pageNum, int pageSize);
}
