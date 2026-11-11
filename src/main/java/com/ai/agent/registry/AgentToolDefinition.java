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
 *
 * <p>summarizer 为 L2 工具专用的确认摘要函数（设计文档 §8.3 confirm_required 的 summary 字段）：
 * 把待执行操作翻译成人类可读的一句话（如「将发布文章《Spring 入门》」）；
 * L0/L1 工具与未提供摘要的 L2 工具为 null，由网关回退到参数摘要。
 */
public record AgentToolDefinition(
        String name,
        String description,
        AgentToolLevel level,
        String module,
        ToolSpecification specification,
        BiFunction<AgentToolContext, String, AgentToolResult> executor,
        BiFunction<AgentToolContext, String, String> summarizer
) {

    /**
     * 生成确认摘要：优先用注册方提供的摘要函数，未提供或生成失败时
     * 回退为「工具名 + 参数预览」，保证 confirm_required 永远带得上 summary。
     */
    public String summarize(AgentToolContext context, String argumentsJson) {
        if (summarizer != null) {
            try {
                String summary = summarizer.apply(context, argumentsJson);
                if (summary != null && !summary.isBlank()) {
                    return summary.trim();
                }
            } catch (Exception ignore) {
                // 摘要生成失败不阻断确认流，走参数预览兜底
            }
        }
        String preview = argumentsJson == null ? "" : argumentsJson.trim();
        if (preview.length() > 120) {
            preview = preview.substring(0, 120) + "...";
        }
        return "执行 " + name + (preview.isEmpty() ? "" : "，参数：" + preview);
    }
}
