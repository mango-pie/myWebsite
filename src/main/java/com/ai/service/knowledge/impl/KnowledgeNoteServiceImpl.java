package com.ai.service.knowledge.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeDocumentMapper;
import com.ai.mapper.knowledge.KnowledgeNoteMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeNoteQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteUpdateRequest;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.model.vo.knowledge.KnowledgeNoteDetailVO;
import com.ai.model.vo.knowledge.KnowledgeNoteVO;
import com.ai.service.BlogPostService;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class KnowledgeNoteServiceImpl implements KnowledgeNoteService {

    private static final int RAW_SUMMARY_LEN = 500;

    @Resource
    private KnowledgeNoteMapper knowledgeNoteMapper;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Resource
    private BlogPostService blogPostService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public Page<KnowledgeNoteVO> page(KnowledgeNoteQueryRequest request, Long userId) {
        int pageNum = request == null ? 1 : request.getPageNum();
        int pageSize = request == null ? 10 : request.getPageSize();
        String keyword = request == null ? null : request.getKeyword();
        String sourceType = request == null ? null : request.getSourceType();
        String publishStatus = request == null ? null : request.getPublishStatus();
        String indexStatus = request == null ? null : request.getIndexStatus();

        List<SourceDocument> ownedSources = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId));
        if (ownedSources.isEmpty()) {
            return new Page<>(pageNum, pageSize, 0);
        }
        Map<Long, SourceDocument> sourceMap = ownedSources.stream()
                .collect(Collectors.toMap(SourceDocument::getId, Function.identity(), (a, b) -> a));
        Set<Long> sourceIds = sourceMap.keySet();

        QueryWrapper wrapper = QueryWrapper.create()
                .in("source_document_id", sourceIds)
                .eq("publish_status", publishStatus, StrUtil.isNotBlank(publishStatus))
                .eq("index_status", indexStatus, StrUtil.isNotBlank(indexStatus))
                .orderBy("update_time", false);

        Page<KnowledgeNote> page = knowledgeNoteMapper.paginate(Page.of(pageNum, pageSize), wrapper);
        List<KnowledgeNoteVO> records = new ArrayList<>();
        for (KnowledgeNote note : page.getRecords()) {
            SourceDocument source = sourceMap.get(note.getSourceDocumentId());
            if (source == null) {
                continue;
            }
            if (StrUtil.isNotBlank(sourceType) && !sourceType.equalsIgnoreCase(source.getSourceType())) {
                continue;
            }
            if (StrUtil.isNotBlank(keyword)) {
                String hay = StrUtil.blankToDefault(note.getTitle(), "")
                        + " "
                        + StrUtil.blankToDefault(note.getTags(), "")
                        + " "
                        + StrUtil.blankToDefault(source.getSourceUrl(), "");
                if (!hay.toLowerCase().contains(keyword.toLowerCase())) {
                    continue;
                }
            }
            records.add(toVO(note, source));
        }
        // keyword/sourceType filter after paginate is imperfect; for V1 re-query when filters need accuracy
        if (StrUtil.isNotBlank(keyword) || StrUtil.isNotBlank(sourceType)) {
            List<KnowledgeNote> all = knowledgeNoteMapper.selectListByQuery(
                    QueryWrapper.create()
                            .in("source_document_id", sourceIds)
                            .eq("publish_status", publishStatus, StrUtil.isNotBlank(publishStatus))
                            .eq("index_status", indexStatus, StrUtil.isNotBlank(indexStatus))
                            .orderBy("update_time", false));
            List<KnowledgeNoteVO> filtered = new ArrayList<>();
            for (KnowledgeNote note : all) {
                SourceDocument source = sourceMap.get(note.getSourceDocumentId());
                if (source == null) {
                    continue;
                }
                if (StrUtil.isNotBlank(sourceType) && !sourceType.equalsIgnoreCase(source.getSourceType())) {
                    continue;
                }
                if (StrUtil.isNotBlank(keyword)) {
                    String hay = StrUtil.blankToDefault(note.getTitle(), "")
                            + " "
                            + StrUtil.blankToDefault(note.getTags(), "")
                            + " "
                            + StrUtil.blankToDefault(source.getSourceUrl(), "");
                    if (!hay.toLowerCase().contains(keyword.toLowerCase())) {
                        continue;
                    }
                }
                filtered.add(toVO(note, source));
            }
            int from = Math.max(0, (pageNum - 1) * pageSize);
            int to = Math.min(filtered.size(), from + pageSize);
            List<KnowledgeNoteVO> slice = from >= filtered.size() ? List.of() : filtered.subList(from, to);
            Page<KnowledgeNoteVO> voPage = new Page<>(pageNum, pageSize, filtered.size());
            voPage.setRecords(slice);
            return voPage;
        }

        Page<KnowledgeNoteVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public KnowledgeNoteDetailVO getDetail(Long noteId, Long userId) {
        KnowledgeNote note = requireOwned(noteId, userId);
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        KnowledgeNoteDetailVO detail = new KnowledgeNoteDetailVO();
        detail.setNote(toVO(note, source));
        detail.setSource(source);
        detail.setDistilledMd(note.getDistilledMd());
        if (source != null) {
            detail.setRawText(source.getRawText());
            detail.setRawTextSummary(summarize(source.getRawText()));
        }
        if (note.getBlogPostId() != null) {
            try {
                BlogPostVO blogPost = blogPostService.getBlogPostVO(note.getBlogPostId());
                detail.setBlogPost(blogPost);
            } catch (Exception ignored) {
                // blog may be deleted
            }
        }
        if (note.getKnowledgeDocumentId() != null) {
            KnowledgeDocument document = knowledgeDocumentMapper.selectOneById(note.getKnowledgeDocumentId());
            if (document != null) {
                detail.setKnowledgeDocument(toDocumentVO(document));
            }
        }
        return detail;
    }

    @Override
    public KnowledgeNoteVO update(Long noteId, KnowledgeNoteUpdateRequest request, Long userId) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求不能为空");
        }
        KnowledgeNote note = requireOwned(noteId, userId);
        LocalDateTime now = LocalDateTime.now();
        if (request.getTitle() != null) {
            note.setTitle(request.getTitle());
        }
        if (request.getTags() != null) {
            note.setTags(request.getTags());
        }
        if (request.getDistilledMd() != null) {
            note.setDistilledMd(request.getDistilledMd());
        }
        note.setLastEditedAt(now);
        note.setUpdateTime(now);
        if (note.getBlogPostId() != null) {
            note.setPublishStatus(KnowledgeNoteConstant.PUBLISH_SYNC_REQUIRED);
        }
        if (note.getKnowledgeDocumentId() != null) {
            note.setIndexStatus(KnowledgeNoteConstant.INDEX_REINDEX_REQUIRED);
        }
        knowledgeNoteMapper.update(note);
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        return toVO(note, source);
    }

    @Override
    public boolean delete(Long noteId, Long userId) {
        KnowledgeNote note = requireOwned(noteId, userId);
        note.setStatus(KnowledgeNoteConstant.STATUS_DELETED);
        note.setUpdateTime(LocalDateTime.now());
        knowledgeNoteMapper.update(note);
        boolean removed = knowledgeNoteMapper.deleteById(noteId) > 0;
        if (removed) {
            opsAuditLogService.audit(
                    OpsAuditActionConstant.READING_NOTE_DELETE,
                    userId,
                    OpsAuditActionConstant.RESOURCE_READING_NOTE,
                    String.valueOf(noteId),
                    true,
                    Map.of("sourceDocumentId", String.valueOf(note.getSourceDocumentId())),
                    null);
        }
        return removed;
    }

    @Override
    public KnowledgeNote requireOwned(Long noteId, Long userId) {
        KnowledgeNote note = knowledgeNoteMapper.selectOneById(noteId);
        if (note == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "精读笔记不存在");
        }
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        if (source == null || !Objects.equals(source.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "精读笔记不存在");
        }
        return note;
    }

    @Override
    public KnowledgeNoteVO toVO(KnowledgeNote note) {
        if (note == null) {
            return null;
        }
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        return toVO(note, source);
    }

    private KnowledgeNoteVO toVO(KnowledgeNote note, SourceDocument source) {
        KnowledgeNoteVO vo = new KnowledgeNoteVO();
        vo.setId(note.getId());
        vo.setSourceDocumentId(note.getSourceDocumentId());
        vo.setBlogPostId(note.getBlogPostId());
        vo.setKnowledgeDocumentId(note.getKnowledgeDocumentId());
        vo.setTitle(note.getTitle());
        vo.setTags(note.getTags());
        vo.setStatus(note.getStatus());
        vo.setPublishStatus(note.getPublishStatus());
        vo.setIndexStatus(note.getIndexStatus());
        vo.setCreateTime(note.getCreateTime());
        vo.setUpdateTime(note.getUpdateTime());
        vo.setLastEditedAt(note.getLastEditedAt());
        vo.setLastPublishedAt(note.getLastPublishedAt());
        vo.setLastIndexedAt(note.getLastIndexedAt());
        if (source != null) {
            vo.setSourceType(source.getSourceType());
            vo.setSourceUrl(source.getSourceUrl());
        }
        return vo;
    }

    private KnowledgeDocumentVO toDocumentVO(KnowledgeDocument document) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        vo.setId(document.getId());
        vo.setKnowledgeBaseId(document.getKnowledgeBaseId());
        vo.setUserId(document.getUserId());
        vo.setSourceDocumentId(document.getSourceDocumentId());
        vo.setFileName(document.getFileName());
        vo.setFileType(document.getFileType());
        vo.setFileSize(document.getFileSize());
        vo.setBucketName(document.getBucketName());
        vo.setObjectKey(document.getObjectKey());
        vo.setParseStatus(document.getParseStatus());
        vo.setChunkCount(document.getChunkCount());
        vo.setErrorMessage(document.getErrorMessage());
        vo.setParsedAt(document.getParsedAt());
        vo.setCreateTime(document.getCreateTime());
        vo.setUpdateTime(document.getUpdateTime());
        return vo;
    }

    private String summarize(String rawText) {
        if (StrUtil.isBlank(rawText)) {
            return "";
        }
        String trimmed = rawText.trim();
        if (trimmed.length() <= RAW_SUMMARY_LEN) {
            return trimmed;
        }
        return trimmed.substring(0, RAW_SUMMARY_LEN) + "...";
    }
}
