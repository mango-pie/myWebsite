package com.ai.service.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.mapper.chat.ChatAgentAuditMapper;
import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.model.entity.ChatAgentAudit;
import com.ai.model.vo.chat.ChatAgentAuditVO;
import com.ai.service.ChatAgentAuditService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnModule("chat")
@Service
public class ChatAgentAuditServiceImpl implements ChatAgentAuditService {

    /** tool_args 为 TEXT 列：截断存储，避免博客正文级参数整段落库 */
    private static final int MAX_ARGS = 4096;
    private static final int MAX_SUMMARY = 512;

    @Resource
    private ChatAgentAuditMapper chatAgentAuditMapper;

    @Override
    public void record(ChatAgentAuditRecord record) {
        if (record == null) {
            return;
        }
        ChatAgentAudit row = ChatAgentAudit.builder()
                .userId(record.getUserId())
                .conversationId(record.getConversationId())
                .toolName(record.getToolName())
                .toolArgs(truncate(record.getToolArgs(), MAX_ARGS))
                .success(record.isSuccess() ? 1 : 0)
                .resultSummary(truncate(record.getResultSummary(), MAX_SUMMARY))
                .createTime(LocalDateTime.now())
                .build();
        try {
            chatAgentAuditMapper.insert(row);
        } catch (Exception e) {
            // 审计失败只告警，不影响 Agent 主流程（模式同 AiUsageLogServiceImpl.record）
            log.warn("Write chat_agent_audit failed: {}", e.getMessage());
        }
    }

    @Override
    public Page<ChatAgentAuditVO> pageLogs(Long userId, String toolName, LocalDate from, LocalDate to,
                                           int pageNum, int pageSize) {
        LocalDate[] range = normalizeRange(from, to);
        int num = Math.max(1, pageNum);
        int size = Math.min(100, Math.max(1, pageSize));
        QueryWrapper query = QueryWrapper.create()
                .ge(ChatAgentAudit::getCreateTime, range[0].atStartOfDay())
                .le(ChatAgentAudit::getCreateTime, range[1].atTime(LocalTime.MAX))
                .orderBy(ChatAgentAudit::getCreateTime, false);
        if (userId != null) {
            query.eq(ChatAgentAudit::getUserId, userId);
        }
        if (StrUtil.isNotBlank(toolName)) {
            query.eq(ChatAgentAudit::getToolName, toolName.trim());
        }
        Page<ChatAgentAudit> page = chatAgentAuditMapper.paginate(Page.of(num, size), query);
        Page<ChatAgentAuditVO> voPage = new Page<>(num, size, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
    }

    private ChatAgentAuditVO toVO(ChatAgentAudit row) {
        return ChatAgentAuditVO.builder()
                .id(row.getId())
                .userId(row.getUserId())
                .conversationId(row.getConversationId())
                .toolName(row.getToolName())
                .toolArgs(row.getToolArgs())
                .success(row.getSuccess() != null && row.getSuccess() == 1)
                .resultSummary(row.getResultSummary())
                .createTime(row.getCreateTime())
                .build();
    }

    private LocalDate[] normalizeRange(LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now();
        LocalDate start = from != null ? from : end.minusDays(6);
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new LocalDate[]{start, end};
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
