package com.ai.agent.registry;

import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.function.BiFunction;

/**
 * 单个 Agent 工具的注册信息：名称/描述/风险级别/所属模块来自注册方，
 * specification 是提供给大模型的函数签名，executor 为实际执行函数
 * （入参：工具上下文 + 原始 JSON 参数字符串，返回统一结果）。
 */
public record AgentToolDefinition(
        String name,
        String description,
        AgentToolLevel level,
        String module,
        ToolSpecification specification,
        BiFunction<AgentToolContext, String, AgentToolResult> executor
) {
}
