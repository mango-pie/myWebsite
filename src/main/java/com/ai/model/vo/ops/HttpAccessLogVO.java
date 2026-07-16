package com.ai.model.vo.ops;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HttpAccessLogVO {
    private Long id;
    private String method;
    private String path;
    private Integer status;
    private Long latencyMs;
    private Long userId;
    private String ip;
    private String traceId;
    private String errorSummary;
    private LocalDateTime createTime;
}
