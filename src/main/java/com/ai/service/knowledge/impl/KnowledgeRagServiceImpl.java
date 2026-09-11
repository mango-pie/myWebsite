package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeRagService;
import com.ai.service.knowledge.KnowledgeVectorStoreService;
import com.ai.setting.runtime.KnowledgeRuntimeSettings;
import org.springframework.stereotype.Service;

import java.util.List;

@ConditionalOnModule("knowledge")
@Service
public class KnowledgeRagServiceImpl implements KnowledgeRagService {

    private final KnowledgeAiModelService aiModelService;
    private final KnowledgeVectorStoreService vectorStoreService;
    private final KnowledgeRuntimeSettings knowledgeRuntimeSettings;

    public KnowledgeRagServiceImpl(KnowledgeAiModelService aiModelService,
                                   KnowledgeVectorStoreService vectorStoreService,
                                   KnowledgeRuntimeSettings knowledgeRuntimeSettings) {
        this.aiModelService = aiModelService;
        this.vectorStoreService = vectorStoreService;
        this.knowledgeRuntimeSettings = knowledgeRuntimeSettings;
    }

    @Override
    public List<KnowledgeChunkVO> searchChunks(Long knowledgeBaseId, Long sourceDocumentId, String question, Integer topK) {
        float[] embedding = aiModelService.embed(question);
        int effectiveTopK = topK == null || topK <= 0 ? knowledgeRuntimeSettings.ragTopK() : topK;
        return vectorStoreService.search(knowledgeBaseId, sourceDocumentId, embedding, effectiveTopK);
    }
}
