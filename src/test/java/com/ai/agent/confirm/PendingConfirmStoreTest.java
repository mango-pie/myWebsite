package com.ai.agent.confirm;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.model.AgentToolContext;
import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingConfirmStoreTest {

    private MutableTicker ticker;

    private PendingConfirmStore store;

    @BeforeEach
    void setUp() {
        ticker = new MutableTicker();
        ChatAgentProperties properties = new ChatAgentProperties();
        properties.setL2ConfirmTtlSeconds(300);
        properties.setL2ConfirmMaxPending(1000);
        store = new PendingConfirmStore(properties, ticker);
    }

    @Test
    void create_returnsTokenAndKeepsArgsForReplay() {
        PendingConfirm pending = createPending(1L);

        assertNotNull(pending);
        assertTrue(pending.confirmToken().startsWith("cfm_"));
        assertEquals("publish_blog_post", pending.toolName());
        assertEquals("{\"id\":42}", pending.toolArgs());
        assertEquals(1L, pending.userId());
        assertEquals(10L, pending.conversationId());
        assertEquals(300, store.ttlSeconds());
    }

    @Test
    void consume_isOneTimeOnly() {
        PendingConfirm pending = createPending(1L);

        PendingConfirm consumed = store.consume(pending.confirmToken(), 1L);
        assertNotNull(consumed);
        assertEquals(pending.confirmToken(), consumed.confirmToken());

        assertNull(store.consume(pending.confirmToken(), 1L));
    }

    @Test
    void consume_rejectsOtherUserAndKeepsPendingForOwner() {
        PendingConfirm pending = createPending(1L);

        assertNull(store.consume(pending.confirmToken(), 2L));

        PendingConfirm consumed = store.consume(pending.confirmToken(), 1L);
        assertNotNull(consumed);
    }

    @Test
    void consume_returnsNullWhenTokenMissing() {
        assertNull(store.consume("cfm_not_exist", 1L));
        assertNull(store.consume(null, 1L));
        assertNull(store.consume("cfm_x", null));
    }

    @Test
    void consume_returnsNullAfterTtlExpired() {
        // 未到期可正常消费
        PendingConfirm pending = createPending(1L);
        ticker.advanceSeconds(299);
        PendingConfirm beforeExpiry = store.consume(pending.confirmToken(), 1L);
        assertNotNull(beforeExpiry);

        // 挂起超过 TTL 后自动过期，无法消费
        PendingConfirm second = createPending(1L);
        ticker.advanceSeconds(301);
        assertNull(store.consume(second.confirmToken(), 1L));
    }

    @Test
    void revoke_removesOwnerPendingOnly() {
        PendingConfirm pending = createPending(1L);

        assertFalse(store.revoke(pending.confirmToken(), 2L));
        assertTrue(store.revoke(pending.confirmToken(), 1L));
        assertNull(store.consume(pending.confirmToken(), 1L));
        assertFalse(store.revoke(pending.confirmToken(), 1L));
    }

    private PendingConfirm createPending(Long userId) {
        AgentToolContext context = AgentToolContext.builder()
                .userId(userId)
                .conversationId(10L)
                .configId("dania")
                .build();
        return store.create("publish_blog_post", "{\"id\":42}", context);
    }

    /** 可手动拨动的假时钟，单位秒，用于验证 TTL 过期。 */
    static class MutableTicker implements Ticker {

        private final AtomicLong nanos = new AtomicLong();

        void advanceSeconds(long seconds) {
            nanos.addAndGet(seconds * 1_000_000_000L);
        }

        @Override
        public long read() {
            return nanos.get();
        }
    }
}
