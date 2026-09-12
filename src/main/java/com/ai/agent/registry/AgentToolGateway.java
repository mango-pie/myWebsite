package com.ai.agent.registry;

import cn.hutool.json.JSONUtil;
import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.context.AgentToolContextHolder;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AgentToolGateway {

    @Resource
    private AgentToolRegistry agentToolRegistry;

    @Resource
    private ChatAgentProperties chatAgentProperties;

    public AgentToolResult execute(String toolName, String argumentsJson, AgentToolContext context) {
        AgentToolDefinition definition = agentToolRegistry.find(toolName)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "未知工具: " + toolName));

        if (definition.level() == AgentToolLevel.L2) {
            if (!chatAgentProperties.isL2Enabled()) {
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
