package com.ai.agent.registry;

import com.ai.agent.model.AgentToolResult;

/**
 * 一次「确认后执行」的完整结果（设计文档 §8.3 确认续跑）：
 * Agent 循环拿 toolName/toolArgs 生成 tool_call / tool_result 事件，
 * 拿 summary 组织确认后的最终回复文本，result 为工具统一执行结果。
 */
public record ConfirmedExecution(
        String toolName,
        String toolArgs,
        String summary,
        AgentToolResult result
) {
}
