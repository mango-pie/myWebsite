package com.ai.model.dto.setting;

import lombok.Data;

import java.util.Map;

@Data
public class SiteSettingUpdateRequest {
    /**
     * key → value；敏感字段传空串表示不修改。
     */
    private Map<String, Object> items;
}
