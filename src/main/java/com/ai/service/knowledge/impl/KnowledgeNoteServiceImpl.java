package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

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
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.service.knowledge.spi.NoteBlogPublisher;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
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

@ConditionalOnModule("knowledge")
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
    private NoteBlogPublisher noteBlogPublisher;

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

        // 搜索/过滤统一下推 SQL（JOIN source_document）：只取命中行的裁剪列 + 当页来源元数据，
        // 不再全量拉笔记/来源到内存过滤（原「分页后再全量重查过滤」的做法随日志增长会劣化）
        String keywordLike = buildLikeKeyword(keyword);
        long totalRow = knowledgeNoteMapper.countSearch(userId, keywordLike, sourceType,
                publishStatus, indexStatus);
        if (totalRow == 0) {
            return new Page<>(pageNum, pageSize, 0);
        }
        List<KnowledgeNote> noteList = knowledgeNoteMapper.selectSearchPage(userId, keywordLike, sourceType,
                publishStatus, indexStatus, (long) (pageNum - 1) * pageSize, pageSize);

        Map<Long, SourceDocument> sourceMap = loadSourceMetaMap(noteList);
        List<KnowledgeNoteVO> records = new ArrayList<>(noteList.size());
        for (KnowledgeNote note : noteList) {
            SourceDocument source = sourceMap.get(note.getSourceDocumentId());
            if (source != null) {
                records.add(toVO(note, source));
            }
        }
        Page<KnowledgeNoteVO> voPage = new Page<>(pageNum, pageSize, totalRow);
        voPage.setRecords(records);
        return voPage;
    }

    /**
     * LIKE 关键词转义：\ % _ 视为字面量（MySQL LIKE 默认转义符为反斜杠），再包 % 做包含匹配。
     * 不 trim，保持与原 contains 语义一致。
     */
    private String buildLikeKeyword(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return null;
        }
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /**
     * 只为当页笔记加载来源元数据三列（id/sourceType/sourceUrl），供 VO 组装。
     */
    private Map<Long, SourceDocument> loadSourceMetaMap(List<KnowledgeNote> notes) {
        if (notes.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = notes.stream()
                .map(KnowledgeNote::getSourceDocumentId)
                .collect(Collectors.toSet());
        return sourceDocumentMapper.selectListByQuery(QueryWrapper.create()
                        .in("id", ids)
                        .select(SourceDocument::getId, SourceDocument::getSourceType, SourceDocument::getSourceUrl))
                .stream()
                .collect(Collectors.toMap(SourceDocument::getId, Function.identity(), (a, b) -> a));
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
            BlogPostVO blogPost = noteBlogPublisher.findBlogPost(note.getBlogPostId());
            if (blogPost != null) {
                detail.setBlogPost(blogPost);
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
        // 归属校验只取两列，不拉 raw_text 全文
        SourceDocument source = sourceDocumentMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", note.getSourceDocumentId())
                .select(SourceDocument::getId, SourceDocument::getUserId));
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
        SourceDocument source = sourceDocumentMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", note.getSourceDocumentId())
                .select(SourceDocument::getId, SourceDocument::getSourceType, SourceDocument::getSourceUrl));
        return toVO(note, source);
    }

    @Override
    public void clearBlogLinkByPostId(Long blogPostId, Long userId) {
        if (blogPostId == null || userId == null) {
            return;
        }
        KnowledgeNote note = knowledgeNoteMapper.selectOneByQuery(
                QueryWrapper.create().eq("blog_post_id", blogPostId));
        if (note == null) {
            return;
        }
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        if (source == null || !Objects.equals(source.getUserId(), userId)) {
            return;
        }
        UpdateChain.of(KnowledgeNote.class)
                .set(KnowledgeNote::getBlogPostId, null)
                .set(KnowledgeNote::getPublishStatus, KnowledgeNoteConstant.PUBLISH_NOT_PUBLISHED)
                .set(KnowledgeNote::getUpdateTime, LocalDateTime.now())
                .where(KnowledgeNote::getId).eq(note.getId())
                .update();
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
