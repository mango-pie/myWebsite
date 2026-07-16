package com.ai.service.knowledge.impl;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.service.knowledge.KnowledgeRagIndexService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeRagIndexServiceImpl implements KnowledgeRagIndexService {

    @Override
    public KnowledgeDocumentVO indexSourceDocument(Long sourceDocumentId, Long userId) {
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "URL/Agent 源文档向量化将在下一阶段接入");
    }

    @Override
    public KnowledgeDocumentVO reindexSourceDocument(Long sourceDocumentId, Long userId) {
        return indexSourceDocument(sourceDocumentId, userId);
    }
}
