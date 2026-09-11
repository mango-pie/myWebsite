package com.ai.service.noop;

import com.ai.model.dto.ops.OpsAuditRecord;
import com.ai.model.vo.ops.OpsAuditLogVO;
import com.ai.service.OpsAuditLogService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Primary
@Service
@ConditionalOnProperty(name = "app.modules.ops", havingValue = "false")
public class NoOpOpsAuditLogService implements OpsAuditLogService {

    @Override
    public void record(OpsAuditRecord record) {
    }

    @Override
    public void audit(String action, Long operatorId, String resourceType, String resourceId,
                      boolean success, Map<String, Object> detail, HttpServletRequest request) {
    }

    @Override
    public Page<OpsAuditLogVO> page(String action, Long operatorId, LocalDate from, LocalDate to,
                                    int pageNum, int pageSize) {
        return new Page<>(pageNum, pageSize, 0);
    }

    @Override
    public int purgeExpired() {
        return 0;
    }
}
