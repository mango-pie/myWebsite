package com.ai.service.knowledge;

import com.ai.model.dto.knowledge.KnowledgeIngestFileRequest;
import com.ai.model.dto.knowledge.KnowledgeIngestUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeProcessRequest;
import com.ai.model.entity.knowledge.SourceDocument;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeIngestionService {

    SourceDocument ingestUrl(KnowledgeProcessRequest request, Long userId);

    SourceDocument ingestUrl(KnowledgeIngestUrlRequest request, Long userId);

    SourceDocument ingestFile(MultipartFile file, KnowledgeIngestFileRequest request, Long userId);

    SourceDocument ingestAgentResult(String title, String url, String rawText, Long knowledgeBaseId, Long userId);
}
