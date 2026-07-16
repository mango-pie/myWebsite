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

/**
 * 全站系统设置实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("site_setting")
public class SiteSetting implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("module")
    private String module;

    @Column("setting_key")
    private String settingKey;

    @Column("setting_value")
    private String settingValue;

    @Column("value_type")
    private String valueType;

    @Column("sensitive")
    private Integer sensitive;

    @Column("updated_by")
    private Long updatedBy;

    @Column("create_time")
    private LocalDateTime createTime;

    @Column("update_time")
    private LocalDateTime updateTime;

    /**
     * 保留字段；本表用物理删除避免与 uk_module_key 冲突。
     */
    @Column("is_delete")
    private Integer isDelete;
}
