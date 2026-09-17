package com.ai.agent.registry;

import com.ai.config.ConditionalOnModule;

import cn.hutool.json.JSONUtil;
import com.ai.agent.context.AgentToolContextHolder;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.runtime.ChatRuntimeSettings;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 工具执行网关：Agent 循环中所有工具调用的唯一入口。
 *
 * <p>职责：按名查注册表 → L2 工具受 agentL2Enabled 开关拦截 →
 * 写入 ThreadLocal 工具上下文 → 执行并记录审计日志；
 * 业务异常与系统异常都转成失败结果返回给模型，不让异常打断 Agent 循环。
 */
@Slf4j
@ConditionalOnModule("chat")
@Component
public class AgentToolGateway {

    @Resource
    private AgentToolRegistry agentToolRegistry;

    @Resource
    private ChatRuntimeSettings chatRuntimeSettings;

    /** 执行一个工具调用；参数为模型产出的原始 JSON 字符串，任何异常都收敛为 fail 结果。 */
    public AgentToolResult execute(String toolName, String argumentsJson, AgentToolContext context) {
        AgentToolDefinition definition = agentToolRegistry.find(toolName)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "未知工具: " + toolName));

        if (definition.level() == AgentToolLevel.L2) {
            if (!chatRuntimeSettings.agentL2Enabled()) {
                return AgentToolResult.fail("工具 " + toolName + " 尚未开放（L2 需用户确认，二期实现）");
            }
        }

        AgentToolContextHolder.set(context);
        try {
            log.info("Agent tool execute: userId={}, tool={}, args={}",
                    context.getUserId(), toolName, abbreviate(argumentsJson));
            AgentToolResult result = definition.executor().apply(context, argumentsJson);
            log.info("Agent tool result: userId={}, tool={}, success={}",
                    context.getUserId(), toolName, result.isSuccess());
            return result;
        } catch (BusinessException e) {
            log.warn("Agent tool business error: tool={}, message={}", toolName, e.getMessage());
            return AgentToolResult.fail(e.getMessage());
        } catch (Exception e) {
            log.error("Agent tool error: tool={}", toolName, e);
            return AgentToolResult.fail("工具执行失败: " + e.getMessage());
        } finally {
            AgentToolContextHolder.clear();
        }
    }

    private String abbreviate(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() <= 500) {
            return trimmed;
        }
        return trimmed.substring(0, 500) + "...";
    }
}
