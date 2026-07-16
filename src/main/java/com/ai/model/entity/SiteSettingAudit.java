package com.ai.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("site_setting_audit")
public class SiteSettingAudit implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("module")
    private String module;

    @Column("setting_key")
    private String settingKey;

    @Column("old_value")
    private String oldValue;

    @Column("new_value")
    private String newValue;

    @Column("operator_id")
    private Long operatorId;

    @Column("action")
    private String action;

    @Column("create_time")
    private LocalDateTime createTime;
}
