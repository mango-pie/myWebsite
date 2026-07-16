package com.ai.service.knowledge.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeDocumentMapper;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeBaseCreateRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteIndexRequest;
import com.ai.model.entity.knowledge.KnowledgeBase;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.model.vo.knowledge.KnowledgeTextChunk;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeBaseService;
import com.ai.service.knowledge.KnowledgeChunkService;
import com.ai.service.knowledge.KnowledgeNoteIndexService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.service.knowledge.KnowledgeVectorStoreService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class KnowledgeNoteIndexServiceImpl implements KnowledgeNoteIndexService {

    @Resource
    private KnowledgeNoteService knowledgeNoteService;

    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeChunkService chunkService;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private KnowledgeVectorStoreService vectorStoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO indexToKnowledgeBase(Long noteId, KnowledgeNoteIndexRequest request, Long userId) {
        KnowledgeNote note = knowledgeNoteService.requireOwned(noteId, userId);
        if (note.getKnowledgeDocumentId() != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该精读已加入知识库，请使用重建索引接口");
        }
        if (StrUtil.isBlank(note.getDistilledMd())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "精读内容为空，无法入库");
        }
        Long knowledgeBaseId = resolveKnowledgeBaseId(request, userId);
        knowledgeBaseService.requireOwned(knowledgeBaseId, userId);

        LocalDateTime now = LocalDateTime.now();
        String fileName = StrUtil.blankToDefault(note.getTitle(), "精读笔记") + ".md";
        byte[] bytes = note.getDistilledMd().getBytes(StandardCharsets.UTF_8);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setUserId(userId);
        document.setSourceDocumentId(note.getSourceDocumentId());
        document.setFileName(fileName);
        document.setFileType(KnowledgeNoteConstant.FILE_TYPE_MD);
        document.setFileSize((long) bytes.length);
        document.setBucketName(KnowledgeNoteConstant.VIRTUAL_BUCKET);
        document.setObjectKey("virtual/note/" + note.getId() + "/" + UUID.randomUUID() + ".md");
        document.setParseStatus("PARSING");
        document.setChunkCount(0);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setIsDelete(0);
        knowledgeDocumentMapper.insert(document);

        try {
            List<KnowledgeTextChunk> chunks = chunkService.split(note.getDistilledMd());
            List<float[]> embeddings = aiModelService.embed(chunks.stream().map(KnowledgeTextChunk::content).toList());
            vectorStoreService.replaceChunks(document, chunks, embeddings);

            document.setParseStatus("PARSED");
            document.setChunkCount(chunks.size());
            document.setParsedAt(now);
            document.setErrorMessage(null);
            document.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.update(document);

            note.setKnowledgeDocumentId(document.getId());
            note.setIndexStatus(KnowledgeNoteConstant.INDEX_INDEXED);
            note.setLastIndexedAt(LocalDateTime.now());
            note.setUpdateTime(LocalDateTime.now());
            knowledgeNoteMapper.update(note);

            SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
            if (source != null) {
                source.setKnowledgeBaseId(knowledgeBaseId);
                source.setKnowledgeDocumentId(document.getId());
                source.setUpdateTime(LocalDateTime.now());
                sourceDocumentMapper.update(source);
            }

            KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
            if (kb != null) {
                KnowledgeBase update = new KnowledgeBase();
                update.setId(kb.getId());
                update.setDocumentCount(kb.getDocumentCount() == null ? 1 : kb.getDocumentCount() + 1);
                update.setUpdateTime(LocalDateTime.now());
                knowledgeBaseService.updateById(update);
            }
            return toVO(document);
        } catch (BusinessException e) {
            markIndexFailed(note, document, e.getMessage());
            throw e;
        } catch (Exception e) {
            markIndexFailed(note, document, e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "加入知识库失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocumentVO reindex(Long noteId, Long userId) {
        KnowledgeNote note = knowledgeNoteService.requireOwned(noteId, userId);
        if (note.getKnowledgeDocumentId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该精读尚未加入知识库，请先入库");
        }
        if (StrUtil.isBlank(note.getDistilledMd())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "精读内容为空，无法重建索引");
        }
        KnowledgeDocument document = knowledgeDocumentMapper.selectOneById(note.getKnowledgeDocumentId());
        if (document == null || !userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "关联的知识库文档不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        try {
            document.setParseStatus("PARSING");
            document.setUpdateTime(now);
            knowledgeDocumentMapper.update(document);

            List<KnowledgeTextChunk> chunks = chunkService.split(note.getDistilledMd());
            List<float[]> embeddings = aiModelService.embed(chunks.stream().map(KnowledgeTextChunk::content).toList());
            vectorStoreService.replaceChunks(document, chunks, embeddings);

            document.setFileName(StrUtil.blankToDefault(note.getTitle(), "精读笔记") + ".md");
            document.setFileSize((long) note.getDistilledMd().getBytes(StandardCharsets.UTF_8).length);
            document.setParseStatus("PARSED");
            document.setChunkCount(chunks.size());
            document.setParsedAt(now);
            document.setErrorMessage(null);
            document.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.update(document);

            note.setIndexStatus(KnowledgeNoteConstant.INDEX_INDEXED);
            note.setLastIndexedAt(LocalDateTime.now());
            note.setUpdateTime(LocalDateTime.now());
            knowledgeNoteMapper.update(note);
            return toVO(document);
        } catch (BusinessException e) {
            note.setIndexStatus(KnowledgeNoteConstant.INDEX_FAILED);
            note.setUpdateTime(LocalDateTime.now());
            knowledgeNoteMapper.update(note);
            throw e;
        } catch (Exception e) {
            note.setIndexStatus(KnowledgeNoteConstant.INDEX_FAILED);
            note.setUpdateTime(LocalDateTime.now());
            knowledgeNoteMapper.update(note);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "重建索引失败：" + e.getMessage());
        }
    }

    private Long resolveKnowledgeBaseId(KnowledgeNoteIndexRequest request, Long userId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择已有知识库或填写新建知识库名称");
        }
        if (request.getKnowledgeBaseId() != null) {
            return request.getKnowledgeBaseId();
        }
        if (StrUtil.isBlank(request.getKnowledgeBaseName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择已有知识库或填写新建知识库名称");
        }
        KnowledgeBaseCreateRequest createRequest = new KnowledgeBaseCreateRequest();
        createRequest.setName(request.getKnowledgeBaseName());
        createRequest.setDescription(request.getKnowledgeBaseDescription());
        createRequest.setVisibility("PRIVATE");
        return knowledgeBaseService.create(createRequest, userId).getId();
    }

    private void markIndexFailed(KnowledgeNote note, KnowledgeDocument document, String message) {
        if (document != null && document.getId() != null) {
            document.setParseStatus("FAILED");
            document.setErrorMessage(message);
            document.setUpdateTime(LocalDateTime.now());
            knowledgeDocumentMapper.update(document);
        }
        note.setIndexStatus(KnowledgeNoteConstant.INDEX_FAILED);
        note.setUpdateTime(LocalDateTime.now());
        knowledgeNoteMapper.update(note);
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument document) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        BeanUtil.copyProperties(document, vo);
        return vo;
    }
}
