package com.ai.agent.confirm;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.model.AgentToolContext;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * L2 待确认状态存储（设计文档 §6.1 / §8.3）：confirmToken → PendingConfirm 的短期挂起表。
 *
 * <p>选型：进程内 Caffeine 内存 + TTL（expireAfterWrite），不做持久化——
 * confirmToken 有效期仅 5 分钟、单实例部署、丢失后用户重发指令即可，
 * 引入 Redis/DB 得不偿失；若未来多实例部署需改为集中式存储（如 Redis）。
 * TTL 与容量上限取启动时 chat.agent 配置，运行期不热更新。
 *
 * <p>confirmToken 一次性消费：{@link #consume} 校验当前用户归属后取走并删除；
 * 过期或容量逐出由 Caffeine 自动清理；toolArgs 原样保存（确认后需原参数重放），
 * 内存量靠 maximumSize 兜底。
 */
@ConditionalOnModule("chat")
@Component
public class PendingConfirmStore {

    private static final String TOKEN_PREFIX = "cfm_";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final Cache<String, PendingConfirm> cache;

    private final long ttlSeconds;

    public PendingConfirmStore(ChatAgentProperties chatAgentProperties) {
        this(chatAgentProperties, Ticker.systemTicker());
    }

    /** 包内可见：注入自定义 Ticker 供单测用假时钟验证 TTL 过期，业务侧走系统时钟。 */
    PendingConfirmStore(ChatAgentProperties chatAgentProperties, Ticker ticker) {
        this.ttlSeconds = Math.max(1, chatAgentProperties.getL2ConfirmTtlSeconds());
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(Math.max(1, chatAgentProperties.getL2ConfirmMaxPending()))
                .ticker(ticker)
                .build();
    }

    /**
     * 创建一条待确认挂起：生成随机 confirmToken，保存工具名与原始参数以便确认后重放。
     */
    public PendingConfirm create(String toolName, String toolArgs, AgentToolContext context) {
        String token = TOKEN_PREFIX + HexUtil.encodeHexStr(randomTokenBytes());
        PendingConfirm pending = new PendingConfirm(
                token,
                toolName,
                toolArgs,
                context == null ? null : context.getUserId(),
                context == null ? null : context.getConversationId(),
                context == null ? null : context.getConfigId(),
                LocalDateTime.now()
        );
        cache.put(token, pending);
        return pending;
    }

    /**
     * 一次性消费：token 存在且属于当前用户时取走并删除，返回挂起状态；
     * 过期/不存在/他人 token 均返回 null（归属校验失败不消费，原待确认仍归原用户所有）。
     *
     * <p>并发安全：先读后用 {@code asMap().remove(key, value)} 原子条件删除，
     * 两个并发确认请求只有一个能取走挂起状态，保证 L2 工具不会被执行两次。
     */
    public PendingConfirm consume(String confirmToken, Long userId) {
        if (StrUtil.isBlank(confirmToken) || userId == null) {
            return null;
        }
        PendingConfirm pending = cache.getIfPresent(confirmToken);
        if (pending == null || !userId.equals(pending.userId())) {
            return null;
        }
        return cache.asMap().remove(confirmToken, pending) ? pending : null;
    }

    /**
     * 用户取消：仅本人可撤销自己的待确认，返回是否实际撤销。
     * 与 {@link #consume} 同样使用原子条件删除，撤销与确认并发时只有一方生效。
     */
    public boolean revoke(String confirmToken, Long userId) {
        if (StrUtil.isBlank(confirmToken) || userId == null) {
            return false;
        }
        PendingConfirm pending = cache.getIfPresent(confirmToken);
        if (pending == null || !userId.equals(pending.userId())) {
            return false;
        }
        return cache.asMap().remove(confirmToken, pending);
    }

    /** 待确认有效期（秒）：confirm_required 事件的 expiresInSec 与配置展示共用。 */
    public long ttlSeconds() {
        return ttlSeconds;
    }

    private static byte[] randomTokenBytes() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        return bytes;
    }
}
