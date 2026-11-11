package com.ai.mapper.chat;

import com.ai.model.entity.ChatAgentAudit;
import com.mybatisflex.core.BaseMapper;

/**
 * Agent 工具审计日志 Mapper；查询统一走 QueryWrapper（见 ChatAgentAuditServiceImpl），暂无自定义 SQL。
 */
public interface ChatAgentAuditMapper extends BaseMapper<ChatAgentAudit> {
}
