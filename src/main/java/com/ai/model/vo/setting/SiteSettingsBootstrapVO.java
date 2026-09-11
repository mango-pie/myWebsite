package com.ai.model.vo.setting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteSettingsBootstrapVO {
    private boolean admin;
    private String siteName;
    private String siteSlogan;
    private List<SettingModuleVO> modules;
}
