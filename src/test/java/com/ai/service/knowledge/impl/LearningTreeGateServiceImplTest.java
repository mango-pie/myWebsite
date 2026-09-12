package com.ai.service.knowledge.impl;

import com.ai.constant.knowledge.LearningTreeIntentConstant;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.model.dto.knowledge.LearningGateRequest;
import com.ai.model.vo.knowledge.LearningDomainVO;
import com.ai.model.vo.knowledge.LearningGateVO;
import com.ai.model.vo.knowledge.LearningTreeVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.LearningTreeService;
import com.ai.service.knowledge.learning.LearningGatePassStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验收脚本 J：门闩低置信返回 confidence、带理由跳过（override）、判定缓存命中不重复烧模型。
 */
@ExtendWith(MockitoExtension.class)
class LearningTreeGateServiceImplTest {

    @Mock
    private LearningTreeService learningTreeService;
    @Mock
    private LearningGatePassStore learningGatePassStore;
    @Mock
    private KnowledgeAiModelService aiModelService;
    @Mock
    private KnowledgeNoteMapper knowledgeNoteMapper;
    @Mock
    private OpsAuditLogService opsAuditLogService;

    private ObjectMapper objectMapper;
    private LearningTreeGateServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        service = new LearningTreeGateServiceImpl();
        ReflectionTestUtils.setField(service, "learningTreeService", learningTreeService);
        ReflectionTestUtils.setField(service, "learningGatePassStore", learningGatePassStore);
        ReflectionTestUtils.setField(service, "aiModelService", aiModelService);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(service, "knowledgeNoteMapper", knowledgeNoteMapper);
        ReflectionTestUtils.setField(service, "opsAuditLogService", opsAuditLogService);

        LearningDomainVO domain = new LearningDomainVO();
        domain.setId(10L);
        domain.setName("Java");
        LearningTreeVO tree = new LearningTreeVO();
        tree.setDomain(domain);
        tree.setBranches(List.of());
        lenient().when(learningTreeService.getTree(10L, 1L, false)).thenReturn(tree);
        lenient().when(learningGatePassStore.issue(any())).thenReturn("pass-1");
    }

    private LearningGateRequest request(String question) {
        LearningGateRequest r = new LearningGateRequest();
        r.setDomainId(10L);
        r.setQuestion(question);
        return r;
    }

    /** 脚本 J：低置信（0.4）原样返回，前端可展示「可能相关」双选项 */
    @Test
    void gate_returnsLowConfidence() {
        when(aiModelService.chat(anyString())).thenReturn("""
                ```json
                {"related": true, "intent": "LEAF", "confidence": 0.4, "reason": "主题相近但不完全确定"}
                ```
                """);

        LearningGateVO vo = service.gate(request("Java 并发原理"), 1L);

        assertTrue(vo.isRelated());
        assertEquals(0.4, vo.getConfidence());
        assertEquals(LearningTreeIntentConstant.LEAF, vo.getIntent());
        assertEquals("pass-1", vo.getGatePassId());
        verify(learningGatePassStore).cacheJudgment(org.mockito.ArgumentMatchers.eq(10L), anyString(), any());
    }

    /** 带理由跳过：不调用 AI，直接签发 overridden pass */
    @Test
    void gate_overrideSkipsAi() {
        LearningGateRequest r = request("这个必须读");
        r.setOverrideIntent(LearningTreeIntentConstant.LEAF);
        r.setOverrideReason("用户确认该问题属于本领域且需要精读");

        LearningGateVO vo = service.gate(r, 1L);

        assertTrue(vo.isOverridden());
        assertEquals(1.0, vo.getConfidence());
        assertEquals(LearningTreeIntentConstant.LEAF, vo.getIntent());
        verify(aiModelService, never()).chat(anyString());
    }

    /** 判定缓存命中：同一 domain+question 不重复调用模型 */
    @Test
    void gate_usesCachedJudgment() {
        LearningGatePassStore.Judgment cached = new LearningGatePassStore.Judgment(
                10L, "Java 并发原理", true, LearningTreeIntentConstant.LEAF,
                "缓存判定", null, List.of("hint1"), 0.9, null, null, null);
        when(learningGatePassStore.getCachedJudgment(10L, "Java 并发原理")).thenReturn(cached);

        LearningGateVO vo = service.gate(request("Java 并发原理"), 1L);

        assertTrue(vo.isRelated());
        assertEquals(0.9, vo.getConfidence());
        assertEquals(LearningTreeIntentConstant.LEAF, vo.getIntent());
        verify(aiModelService, never()).chat(anyString());
    }
}
