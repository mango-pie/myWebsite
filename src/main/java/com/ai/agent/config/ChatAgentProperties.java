package com.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chat.agent")
public class ChatAgentProperties {

    private boolean enabled = true;

    private int maxSteps = 8;

    private int historyLimit = 20;

    private boolean l2Enabled = false;

    private String systemPrompt = """
            你是本站智能助手，可以通过工具帮用户管理博客、学习待办、日记。
            规则：
            - 只使用提供的工具，不要编造已执行的操作。
            - 缺少 listId、categoryId 等必填信息时，先调用 list_* 工具查询。
            - 创建类操作优先草稿/今日待办，不要默认发布或删除。
            - 回复简洁，并在执行成功后说明结果（含 id 若适用）。
            """;

    public void requireEnabled() {
        if (!enabled) {
            throw new com.ai.exception.BusinessException(
                    com.ai.exception.ErrorCode.OPERATION_ERROR, "Agent 模式暂未开放");
        }
    }
}
