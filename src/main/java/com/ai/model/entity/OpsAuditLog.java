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
@Table("ops_audit_log")
public class OpsAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("operator_id")
    private Long operatorId;

    @Column("action")
    private String action;

    @Column("resource_type")
    private String resourceType;

    @Column("resource_id")
    private String resourceId;

    @Column("ip")
    private String ip;

    @Column("detail_json")
    private String detailJson;

    @Column("success")
    private Integer success;

    @Column("create_time")
    private LocalDateTime createTime;
}
