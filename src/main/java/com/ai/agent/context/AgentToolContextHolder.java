package com.ai.agent.context;

import com.ai.agent.model.AgentToolContext;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;

/**
 * Agent 工具上下文的 ThreadLocal 桥接。
 *
 * <p>由 {@code AgentToolGateway#execute} 在执行工具前写入、finally 中清理；
 * 供无法通过方法参数拿到 {@link AgentToolContext} 的深层调用（如 service 内部）获取当前用户。
 */
public final class AgentToolContextHolder {

    private static final ThreadLocal<AgentToolContext> CONTEXT = new ThreadLocal<>();

    private AgentToolContextHolder() {
    }

    public static void set(AgentToolContext context) {
        CONTEXT.set(context);
    }

    public static AgentToolContext get() {
        return CONTEXT.get();
    }

    public static Long requireUserId() {
        AgentToolContext context = CONTEXT.get();
        if (context == null || context.getUserId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Agent 工具上下文缺失");
        }
        return context.getUserId();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
