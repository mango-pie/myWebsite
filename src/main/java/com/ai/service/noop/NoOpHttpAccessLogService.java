package com.ai.service.noop;

import com.ai.model.vo.ops.HttpAccessLogVO;
import com.ai.service.HttpAccessLogService;
import com.mybatisflex.core.paginate.Page;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Primary
@Service
@ConditionalOnProperty(name = "app.modules.ops", havingValue = "false")
public class NoOpHttpAccessLogService implements HttpAccessLogService {

    @Override
    public void record(String method, String path, Integer status, Long latencyMs,
                       Long userId, String ip, String traceId, String errorSummary) {
    }

    @Override
    public Page<HttpAccessLogVO> page(Integer status, String statusClass, String pathPrefix,
                                      LocalDate from, LocalDate to, int pageNum, int pageSize) {
        return new Page<>(pageNum, pageSize, 0);
    }

    @Override
    public int purgeExpired() {
        return 0;
    }
}
