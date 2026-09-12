package com.ai.service.knowledge.learning;

import com.ai.constant.knowledge.LearningTreeIntentConstant;
import com.ai.constant.knowledge.LearningTreeRedisConstant;
import com.ai.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningGatePassStoreTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private LearningGatePassStore store;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new LearningGatePassStore();
        ReflectionTestUtils.setField(store, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(store, "redisAvailable", false);
        store.clearAll();
    }

    @Test
    void issueAndRequireValid() {
        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent("LEAF")
                .goal("HashMap")
                .build();
        String id = store.issue(pass);
        assertNotNull(store.get(id));
        store.requireValid(1L, 10L, id);
    }

    @Test
    void wrongUserRejected() {
        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent("LEAF")
                .goal("x")
                .build();
        String id = store.issue(pass);
        assertThrows(Exception.class, () -> store.requireValid(2L, 10L, id));
    }

    @Test
    void validateForV2_rejectsNonLeafIntent() {
        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent(LearningTreeIntentConstant.BRANCH)
                .goal("IO")
                .build();
        String id = store.issue(pass);
        assertThrows(BusinessException.class, () -> store.validateForV2(1L, 10L, id, true));
    }

    @Test
    void validateForV2_allowsLeaf() {
        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent(LearningTreeIntentConstant.LEAF)
                .goal("HashMap")
                .build();
        String id = store.issue(pass);
        store.validateForV2(1L, 10L, id, true);
        assertNotNull(store.get(id));
    }

    @Test
    void validateForV2_allowsOverriddenBranch() {
        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent(LearningTreeIntentConstant.BRANCH)
                .overridden(true)
                .overrideReason("用户带理由跳过")
                .goal("IO")
                .build();
        String id = store.issue(pass);
        // 人工 override 的 pass 不受 LEAF 强校验限制
        store.validateForV2(1L, 10L, id, true);
    }

    @Test
    void redisIssueAndGet() {
        enableRedis();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent(LearningTreeIntentConstant.LEAF)
                .goal("HashMap")
                .build();
        String id = store.issue(pass);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                keyCaptor.capture(),
                jsonCaptor.capture(),
                eq(LearningTreeRedisConstant.GATE_PASS_TTL_SECONDS),
                eq(TimeUnit.SECONDS));
        assertEquals(LearningTreeRedisConstant.gatePassKey(id), keyCaptor.getValue());

        when(valueOperations.get(LearningTreeRedisConstant.gatePassKey(id))).thenReturn(jsonCaptor.getValue());
        LearningGatePass loaded = store.get(id);
        assertNotNull(loaded);
        assertEquals(1L, loaded.getUserId());
        assertEquals(10L, loaded.getDomainId());
        assertEquals(LearningTreeIntentConstant.LEAF, loaded.getIntent());
    }

    @Test
    void redisIssueFailureFallsBackToMemory() {
        enableRedis();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RuntimeException("redis down")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("redis down"));

        LearningGatePass pass = LearningGatePass.builder()
                .userId(1L)
                .domainId(10L)
                .related(true)
                .intent(LearningTreeIntentConstant.LEAF)
                .goal("HashMap")
                .build();
        String id = store.issue(pass);
        assertNotNull(store.get(id));
    }

    private void enableRedis() {
        ReflectionTestUtils.setField(store, "redisAvailable", true);
        ReflectionTestUtils.setField(store, "stringRedisTemplate", stringRedisTemplate);
    }
}
