package com.ai.agent.confirm;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.model.dto.chat.ChatAgentAuditRecord;
import com.ai.service.ChatAgentAuditService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Agent L2 确认流接入服务（设计文档 §8.3）：面向 Controller 的确认/取消入口。
 *
 * <p>确认续跑走既有聊天接口（confirmToken 随 ChatRequest 进入 Agent 门面）；
 * 本服务承载取消路径：一次性取走本人的待确认状态并落审计表（工具未执行），
 * 令牌不存在 / 已过期 / 非本人一律返回 false，不区分原因避免探测他人令牌。
 */
@Slf4j
@ConditionalOnModule("chat")
@Service
public class ChatAgentConfirmService {

    @Resource
    private PendingConfirmStore pendingConfirmStore;

    @Resource
    private ChatAgentAuditService chatAgentAuditService;

    /**
     * 取消一个待确认操作：仅本人可取消自己的挂起；取消成功即令牌作废，
     * 之后再确认会提示已过期。取消动作落审计表（success=false，未执行任何工具）。
     *
     * @return 是否实际取消（false = 已过期 / 不存在 / 非本人）
     */
    public boolean cancel(Long userId, String confirmToken) {
        if (userId == null || StrUtil.isBlank(confirmToken)) {
            return false;
        }
        // consume 为归属校验 + 原子一次性取走：拿到内容才能在审计里记录被取消的工具
        PendingConfirm pending = pendingConfirmStore.consume(confirmToken, userId);
        if (pending == null) {
            log.info("Agent confirm cancel miss: userId={}", userId);
            return false;
        }
        log.info("Agent confirm cancelled: userId={}, tool={}", userId, pending.toolName());
        chatAgentAuditService.record(ChatAgentAuditRecord.builder()
                .userId(userId)
                .conversationId(pending.conversationId())
                .toolName(pending.toolName())
                .toolArgs(pending.toolArgs())
                .success(false)
                .resultSummary("用户取消，未执行")
                .build());
        return true;
    }
}
