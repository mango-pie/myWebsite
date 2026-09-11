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
@Table("http_access_log")
public class HttpAccessLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("method")
    private String method;

    @Column("path")
    private String path;

    @Column("status")
    private Integer status;

    @Column("latency_ms")
    private Long latencyMs;

    @Column("user_id")
    private Long userId;

    @Column("ip")
    private String ip;

    @Column("trace_id")
    private String traceId;

    @Column("error_summary")
    private String errorSummary;

    @Column("create_time")
    private LocalDateTime createTime;
}
