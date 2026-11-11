package com.ai.service;

import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.model.vo.chat.ChatAgentAuditVO;
import com.mybatisflex.core.paginate.Page;

import java.time.LocalDate;

/**
 * Agent 工具审计服务（设计文档 §8.2 chat_agent_audit）：
 * 记录每次工具调用的工具名 / 参数 / 结果摘要，供排查「AI 误创建」类问题。
 */
public interface ChatAgentAuditService {

    /**
     * 记录一次工具调用审计；参数与结果摘要由服务端截断，
     * 写入失败仅告警不打断 Agent 循环。
     */
    void record(ChatAgentAuditRecord record);

    /**
     * 分页查询审计日志，按创建时间倒序。
     *
     * @param userId   归属过滤：非空时只查该用户（对齐当前用户归属校验），
     *                 为空时查全量（仅限管理/运维端点使用，端点侧自行鉴权）
     * @param toolName 工具名精确过滤，可空
     */
    Page<ChatAgentAuditVO> pageLogs(Long userId, String toolName, LocalDate from, LocalDate to,
                                    int pageNum, int pageSize);
}
