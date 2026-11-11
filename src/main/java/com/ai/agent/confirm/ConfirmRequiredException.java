package com.ai.agent.confirm;

/**
 * L2 工具待用户确认信号（设计文档 §8.3）：网关执行 L2 工具且没有有效 confirmToken 时抛出，
 * 由 Agent 循环捕获后向流推送 confirm_required 事件并暂停，等用户确认后带 token 重放执行。
 */
public class ConfirmRequiredException extends RuntimeException {

    private final String confirmToken;

    private final String toolName;

    private final String summary;

    public ConfirmRequiredException(String confirmToken, String toolName, String summary) {
        super("工具 " + toolName + " 需要用户确认");
        this.confirmToken = confirmToken;
        this.toolName = toolName;
        this.summary = summary;
    }

    public String getConfirmToken() {
        return confirmToken;
    }

    public String getToolName() {
        return toolName;
    }

    public String getSummary() {
        return summary;
    }
}
