package com.ai.agent.registry;

import com.ai.config.ConditionalOnModule;

import cn.hutool.json.JSONUtil;
import com.ai.agent.confirm.ConfirmRequiredException;
import com.ai.agent.confirm.PendingConfirm;
import com.ai.agent.confirm.PendingConfirmStore;
import com.ai.agent.context.AgentToolContextHolder;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.service.ChatAgentAuditService;
import com.ai.setting.runtime.ChatRuntimeSettings;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 工具执行网关：Agent 循环中所有工具调用的唯一入口。
 *
 * <p>职责：按名查注册表 → L2 工具走确认流（设计文档 §8.3：未带确认令牌时保存挂起状态
 * 并抛 {@link ConfirmRequiredException}，由 Agent 循环推送 confirm_required 暂停；
 * 用户确认后经 {@link #executeConfirmed} 一次性消费令牌原参数重放）→
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

    @Resource
    private PendingConfirmStore pendingConfirmStore;

    @Resource
    private ChatAgentAuditService chatAgentAuditService;

    /** 执行一个工具调用；参数为模型产出的原始 JSON 字符串，任何异常都收敛为 fail 结果。 */
    public AgentToolResult execute(String toolName, String argumentsJson, AgentToolContext context) {
        AgentToolDefinition definition = agentToolRegistry.find(toolName)
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "未知工具: " + toolName));

        if (definition.level() == AgentToolLevel.L2) {
            if (!chatRuntimeSettings.agentL2Enabled()) {
                return AgentToolResult.fail("工具 " + toolName + " 尚未开放（L2 确认流程未开启）");
            }
            // L2 开启但未经确认：保存挂起状态并要求确认，工具绝不直接执行
            PendingConfirm pending = pendingConfirmStore.create(toolName, argumentsJson, context);
            throw new ConfirmRequiredException(
                    pending.confirmToken(), toolName, definition.summarize(context, argumentsJson));
        }

        return doExecute(definition, toolName, argumentsJson, context);
    }

    /**
     * 确认续跑（设计文档 §8.3）：用户确认后一次性消费 confirmToken，
     * 按挂起时保存的工具名与原始参数重放执行。
     *
     * <p>令牌过期 / 不存在 / 非本人 / 与当前会话不一致均抛业务异常，
     * 由 Agent 门面转成 error 事件；工具此时并未执行，不会留下半完成状态。
     */
    public ConfirmedExecution executeConfirmed(String confirmToken, AgentToolContext context) {
        if (!chatRuntimeSettings.agentL2Enabled()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "L2 确认流程未开启");
        }
        PendingConfirm pending = pendingConfirmStore.consume(confirmToken, context.getUserId());
        if (pending == null) {
            log.warn("Agent confirm rejected: userId={}, token invalid/expired/not-owner", context.getUserId());
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "确认已过期或无效，请重新发起指令");
        }
        if (pending.conversationId() == null
                || !pending.conversationId().equals(context.getConversationId())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "确认信息与当前会话不一致，请重新发起指令");
        }
        AgentToolDefinition definition = agentToolRegistry.find(pending.toolName())
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "未知工具: " + pending.toolName()));
        AgentToolResult result = doExecute(definition, pending.toolName(), pending.toolArgs(), context);
        return new ConfirmedExecution(
                pending.toolName(), pending.toolArgs(), definition.summarize(context, pending.toolArgs()), result);
    }

    /** 统一执行体：设置工具上下文、执行、记录审计；异常收敛为 fail 结果。 */
    private AgentToolResult doExecute(AgentToolDefinition definition, String toolName,
                                      String argumentsJson, AgentToolContext context) {
        AgentToolContextHolder.set(context);
        try {
            log.info("Agent tool execute: userId={}, tool={}, args={}",
                    context.getUserId(), toolName, abbreviate(argumentsJson));
            AgentToolResult result = definition.executor().apply(context, argumentsJson);
            log.info("Agent tool result: userId={}, tool={}, success={}",
                    context.getUserId(), toolName, result.isSuccess());
            recordAudit(context, toolName, argumentsJson, result);
            return result;
        } catch (BusinessException e) {
            log.warn("Agent tool business error: tool={}, message={}", toolName, e.getMessage());
            AgentToolResult result = AgentToolResult.fail(e.getMessage());
            recordAudit(context, toolName, argumentsJson, result);
            return result;
        } catch (Exception e) {
            log.error("Agent tool error: tool={}", toolName, e);
            AgentToolResult result = AgentToolResult.fail("工具执行失败: " + e.getMessage());
            recordAudit(context, toolName, argumentsJson, result);
            return result;
        } finally {
            AgentToolContextHolder.clear();
        }
    }

    /**
     * 落审计表（设计文档 §8.2）：确认后的执行、直接执行、取消与失败最终都经此记录；
     * 参数与结果摘要由审计服务截断，写入失败在服务内部吞掉只告警。
     */
    private void recordAudit(AgentToolContext context, String toolName, String argumentsJson,
                             AgentToolResult result) {
        chatAgentAuditService.record(ChatAgentAuditRecord.builder()
                .userId(context.getUserId())
                .conversationId(context.getConversationId())
                .toolName(toolName)
                .toolArgs(argumentsJson)
                .success(result.isSuccess())
                .resultSummary(result.isSuccess() ? toJsonOrNull(result.getData()) : result.getError())
                .build());
    }

    private String toJsonOrNull(Map<String, Object> data) {
        return data == null || data.isEmpty() ? null : JSONUtil.toJsonStr(data);
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
