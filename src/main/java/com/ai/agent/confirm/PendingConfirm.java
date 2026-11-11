package com.ai.agent.confirm;

import java.time.LocalDateTime;

/**
 * 一条 L2 待确认挂起状态（设计文档 §8.3）：confirm_required 发出到用户确认/取消之间的快照。
 *
 * <p>toolArgs 保存模型产出的原始参数 JSON，确认后需原样重放执行，故不做截断；
 * userId 用于确认时的当前用户归属校验，防止拿到他人 token 替其确认。
 */
public record PendingConfirm(
        String confirmToken,
        String toolName,
        String toolArgs,
        Long userId,
        Long conversationId,
        String configId,
        LocalDateTime createdAt
) {
}
