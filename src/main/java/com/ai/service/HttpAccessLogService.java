package com.ai.service;

import com.ai.model.vo.ops.HttpAccessLogVO;
import com.mybatisflex.core.paginate.Page;

import java.time.LocalDate;

public interface HttpAccessLogService {

    void record(String method, String path, Integer status, Long latencyMs,
                Long userId, String ip, String traceId, String errorSummary);

    Page<HttpAccessLogVO> page(Integer status, String statusClass, String pathPrefix,
                               LocalDate from, LocalDate to, int pageNum, int pageSize);

    int purgeExpired();
}
