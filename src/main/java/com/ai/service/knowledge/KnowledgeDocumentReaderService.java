package com.ai.service.knowledge;

public interface KnowledgeDocumentReaderService {

    String read(byte[] content, String fileType);
}
