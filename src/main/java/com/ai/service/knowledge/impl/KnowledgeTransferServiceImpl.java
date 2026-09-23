package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import com.ai.model.dto.knowledge.KnowledgeIngestFileRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteIndexRequest;
import com.ai.model.dto.knowledge.KnowledgeTransferFileRequest;
import com.ai.model.dto.knowledge.KnowledgeTransferResult;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.service.knowledge.KnowledgeDistillationService;
import com.ai.service.knowledge.KnowledgeIngestionService;
import com.ai.service.knowledge.KnowledgeNoteIndexService;
import com.ai.service.knowledge.KnowledgeTransferService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@link KnowledgeTransferService} 实现：把原 LibraryServiceImpl 里的
 * 跨模块编排（ingest → distill → index）收口到 knowledge 模块内部，
 * 库外模块只接触中立 DTO。
 */
@Slf4j
@ConditionalOnModule("knowledge")
@Service
public class KnowledgeTransferServiceImpl implements KnowledgeTransferService {

    @Resource
    private KnowledgeIngestionService ingestionService;

    @Resource
    private KnowledgeDistillationService distillationService;

    @Resource
    private KnowledgeNoteIndexService indexService;

    @Override
    public KnowledgeTransferResult transferFile(KnowledgeTransferFileRequest request, Long userId) {
        String fileName = StrUtil.blankToDefault(request.getFileName(), "file");
        KnowledgeIngestFileRequest ingestRequest = new KnowledgeIngestFileRequest();
        ingestRequest.setTitle(stripExt(fileName));
        ingestRequest.setTags(request.getTags());

        SourceDocument source = ingestionService.ingestFile(
                toMultipartFile(request), ingestRequest, userId);
        KnowledgeNote note = distillationService.distillToMarkdown(
                source.getId(), userId, request.getTags());

        boolean indexed = false;
        try {
            indexService.indexToKnowledgeBase(note.getId(), new KnowledgeNoteIndexRequest(), userId);
            indexed = true;
        } catch (Exception e) {
            log.warn("Knowledge transfer index step failed, noteId={}: {}", note.getId(), e.getMessage());
        }
        log.info("File transferred into knowledge, sourceId={}, noteId={}, indexed={}",
                source.getId(), note.getId(), indexed);
        return KnowledgeTransferResult.builder()
                .sourceDocumentId(source.getId())
                .noteId(note.getId())
                .noteTitle(note.getTitle())
                .indexed(indexed)
                .build();
    }

    private String stripExt(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "").trim();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private MultipartFile toMultipartFile(KnowledgeTransferFileRequest request) {
        byte[] content = request.getContent() == null ? new byte[0] : request.getContent();
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return request.getFileName();
            }

            @Override
            public String getContentType() {
                return request.getContentType();
            }

            @Override
            public boolean isEmpty() {
                return content.length == 0;
            }

            @Override
            public long getSize() {
                return content.length;
            }

            @Override
            public byte[] getBytes() {
                return content;
            }

            @Override
            public InputStream getInputStream() {
                return new ByteArrayInputStream(content);
            }

            @Override
            public void transferTo(File dest) throws IOException {
                Files.write(dest.toPath(), content);
            }

            @Override
            public void transferTo(Path dest) throws IOException {
                Files.write(dest, content);
            }
        };
    }
}
