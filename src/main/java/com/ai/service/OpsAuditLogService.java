package com.ai.service;

import com.ai.model.dto.ops.OpsAuditRecord;
import com.ai.model.vo.ops.OpsAuditLogVO;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDate;
import java.util.Map;

public interface OpsAuditLogService {

    void record(OpsAuditRecord record);

    void audit(String action, Long operatorId, String resourceType, String resourceId,
               boolean success, Map<String, Object> detail, HttpServletRequest request);

    Page<OpsAuditLogVO> page(String action, Long operatorId, LocalDate from, LocalDate to, int pageNum, int pageSize);

    int purgeExpired();
}
