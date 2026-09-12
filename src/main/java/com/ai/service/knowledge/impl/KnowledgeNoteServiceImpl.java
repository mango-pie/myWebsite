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
import com.ai.model.vo.knowledge.KnowledgeTagVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.knowledge.KnowledgeNoteService;
import com.ai.service.knowledge.LearningTreeService;
import com.ai.service.knowledge.spi.NoteBlogPublisher;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    @Lazy
    @Resource
    private LearningTreeService learningTreeService;

    @Override
    public Page<KnowledgeNoteVO> page(KnowledgeNoteQueryRequest request, Long userId) {
        int pageNum = request == null ? 1 : request.getPageNum();
        int pageSize = request == null ? 10 : request.getPageSize();
        String keyword = request == null ? null : request.getKeyword();
        String sourceType = request == null ? null : request.getSourceType();
        String publishStatus = request == null ? null : request.getPublishStatus();
        String indexStatus = request == null ? null : request.getIndexStatus();
        String reviewStatus = request == null ? null : request.getReviewStatus();
        // 归档过滤：null 默认只看未归档（隐藏归档），true 只看归档，false 看全部
        Boolean archived = request == null ? null : request.getArchived();

        // 归档过滤：null 默认只看未归档（隐藏归档），true 只看归档，false 看全部
        Integer archivedValue = resolveArchivedValue(archived);
        // 搜索/过滤统一下推 SQL（JOIN source_document）：只取命中行的裁剪列 + 当页来源元数据，
        // 不再全量拉笔记/来源到内存过滤
        String keywordLike = buildLikeKeyword(keyword);

        long totalRow = knowledgeNoteMapper.countSearch(userId, keywordLike, sourceType,
                publishStatus, indexStatus, reviewStatus, archivedValue);
        if (totalRow == 0) {
            return new Page<>(pageNum, pageSize, 0);
        }
        List<KnowledgeNote> noteList = knowledgeNoteMapper.selectSearchPage(userId, keywordLike, sourceType,
                publishStatus, indexStatus, reviewStatus, archivedValue,
                (long) (pageNum - 1) * pageSize, pageSize);

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
     * 归档过滤：null 默认只看未归档（列表隐藏归档），true 只看归档，false 全部。
     */
    private Integer resolveArchivedValue(Boolean archived) {
        if (archived == null) {
            return 0;
        }
        return archived ? 1 : null;
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
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeNoteVO updateReviewStatus(Long noteId, String reviewStatus, Long userId) {
        if (StrUtil.isBlank(reviewStatus)
                || (!KnowledgeNoteConstant.REVIEW_NEW.equals(reviewStatus)
                && !KnowledgeNoteConstant.REVIEW_REVIEWING.equals(reviewStatus)
                && !KnowledgeNoteConstant.REVIEW_MASTERED.equals(reviewStatus))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "reviewStatus 仅支持 NEW / REVIEWING / MASTERED");
        }
        KnowledgeNote note = requireOwned(noteId, userId);
        note.setReviewStatus(reviewStatus);
        note.setUpdateTime(LocalDateTime.now());
        knowledgeNoteMapper.update(note);
        return toVO(note);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeNoteVO archive(Long noteId, boolean archived, Long userId) {
        KnowledgeNote note = requireOwned(noteId, userId);
        note.setIsArchived(archived ? 1 : 0);
        note.setUpdateTime(LocalDateTime.now());
        knowledgeNoteMapper.update(note);
        return toVO(note);
    }

    @Override
    public byte[] exportAll(Long userId) {
        // 导出只需要溯源元数据，不需要 raw_text 全文（蒸馏正文来自 note.distilledMd）
        List<SourceDocument> ownedSources = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("is_delete", 0)
                        .select(SourceDocument::getId, SourceDocument::getSourceType, SourceDocument::getSourceUrl));
        if (ownedSources.isEmpty()) {
            return new byte[0];
        }
        Set<Long> sourceIds = ownedSources.stream().map(SourceDocument::getId).collect(Collectors.toSet());
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectListByQuery(
                QueryWrapper.create()
                        .in("source_document_id", sourceIds)
                        .eq("status", KnowledgeNoteConstant.STATUS_SUCCESS)
                        .orderBy("update_time", false));
        Map<Long, SourceDocument> sourceMap = ownedSources.stream()
                .collect(Collectors.toMap(SourceDocument::getId, Function.identity(), (a, b) -> a));
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(baos)) {
                StringBuilder index = new StringBuilder("# 精读库导出\n\n");
                for (KnowledgeNote note : notes) {
                    SourceDocument source = sourceMap.get(note.getSourceDocumentId());
                    String fileName = safeFileName(note.getId(), note.getTitle()) + ".md";
                    StringBuilder md = new StringBuilder();
                    md.append("---\n");
                    md.append("id: ").append(note.getId()).append('\n');
                    md.append("title: ").append(oneLine(note.getTitle())).append('\n');
                    md.append("tags: ").append(oneLine(note.getTags())).append('\n');
                    md.append("sourceUrl: ").append(source == null ? "" : oneLine(source.getSourceUrl())).append('\n');
                    md.append("sourceType: ").append(source == null ? "" : oneLine(source.getSourceType())).append('\n');
                    md.append("reviewStatus: ").append(note.getReviewStatus() == null ? KnowledgeNoteConstant.REVIEW_NEW : note.getReviewStatus()).append('\n');
                    md.append("createdAt: ").append(note.getCreateTime() == null ? "" : note.getCreateTime()).append('\n');
                    md.append("---\n\n");
                    md.append(StrUtil.blankToDefault(note.getDistilledMd(), "")).append('\n');
                    zip.putNextEntry(new java.util.zip.ZipEntry(fileName));
                    zip.write(md.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    zip.closeEntry();
                    index.append("- [").append(oneLine(note.getTitle())).append("](").append(fileName).append(")\n");
                }
                zip.putNextEntry(new java.util.zip.ZipEntry("index.md"));
                zip.write(index.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "导出失败：" + e.getMessage());
        }
    }

    @Override
    public List<KnowledgeTagVO> listTags(Long userId) {
        List<SourceDocument> ownedSources = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("is_delete", 0)
                        .select(SourceDocument::getId));
        if (ownedSources.isEmpty()) {
            return List.of();
        }
        Set<Long> sourceIds = ownedSources.stream().map(SourceDocument::getId).collect(Collectors.toSet());
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectListByQuery(
                QueryWrapper.create().in("source_document_id", sourceIds));
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (KnowledgeNote note : notes) {
            if (StrUtil.isBlank(note.getTags())) {
                continue;
            }
            for (String tag : note.getTags().split(",")) {
                String t = tag.trim();
                if (t.isEmpty() || t.startsWith("agentQuery:")) {
                    continue;
                }
                counts.merge(t, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> {
                    KnowledgeTagVO vo = new KnowledgeTagVO();
                    vo.setName(e.getKey());
                    vo.setCount(e.getValue());
                    return vo;
                })
                .toList();
    }

    @Override
    public int renameTag(String oldName, String newName, Long userId) {
        String oldTag = normalizeTag(oldName);
        String newTag = normalizeTag(newName);
        if (oldTag.isEmpty() || newTag.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名不能为空");
        }
        if (oldTag.equals(newTag)) {
            return 0;
        }
        List<SourceDocument> ownedSources = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("is_delete", 0)
                        .select(SourceDocument::getId));
        if (ownedSources.isEmpty()) {
            return 0;
        }
        Set<Long> sourceIds = ownedSources.stream().map(SourceDocument::getId).collect(Collectors.toSet());
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectListByQuery(
                QueryWrapper.create().in("source_document_id", sourceIds));
        int affected = 0;
        for (KnowledgeNote note : notes) {
            String updated = replaceTag(note.getTags(), oldTag, newTag);
            if (!Objects.equals(updated, note.getTags())) {
                note.setTags(updated);
                note.setUpdateTime(LocalDateTime.now());
                knowledgeNoteMapper.update(note);
                affected++;
            }
        }
        return affected;
    }

    @Override
    public int deleteTag(String name, Long userId) {
        String tag = normalizeTag(name);
        if (tag.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名不能为空");
        }
        List<SourceDocument> ownedSources = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("is_delete", 0)
                        .select(SourceDocument::getId));
        if (ownedSources.isEmpty()) {
            return 0;
        }
        Set<Long> sourceIds = ownedSources.stream().map(SourceDocument::getId).collect(Collectors.toSet());
        List<KnowledgeNote> notes = knowledgeNoteMapper.selectListByQuery(
                QueryWrapper.create().in("source_document_id", sourceIds));
        int affected = 0;
        for (KnowledgeNote note : notes) {
            String updated = replaceTag(note.getTags(), tag, null);
            if (!Objects.equals(updated, note.getTags())) {
                note.setTags(updated);
                note.setUpdateTime(LocalDateTime.now());
                knowledgeNoteMapper.update(note);
                affected++;
            }
        }
        return affected;
    }

    private String replaceTag(String tags, String oldTag, String newTag) {
        if (StrUtil.isBlank(tags)) {
            return tags;
        }
        List<String> result = new ArrayList<>();
        for (String part : tags.split(",")) {
            String t = part.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.equals(oldTag)) {
                if (newTag != null && !newTag.isEmpty()) {
                    result.add(newTag);
                }
            } else {
                result.add(t);
            }
        }
        return String.join(",", result);
    }

    private String normalizeTag(String tag) {
        return tag == null ? "" : tag.trim().replaceAll("\\s+", "");
    }

    private String safeFileName(Long id, String title) {
        String t = oneLine(StrUtil.blankToDefault(title, "note"));
        t = t.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (t.length() > 60) {
            t = t.substring(0, 60);
        }
        return id + "-" + t;
    }

    private String oneLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\r?\\n", " ").trim();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long noteId, Long userId) {
        KnowledgeNote note = requireOwned(noteId, userId);
        Long blogPostId = note.getBlogPostId();
        Long sourceDocumentId = note.getSourceDocumentId();

        // 1) 领域树摘叶（幂等）
        learningTreeService.detachLeafByNote(noteId, userId);

        // 2) 删精读
        note.setStatus(KnowledgeNoteConstant.STATUS_DELETED);
        note.setUpdateTime(LocalDateTime.now());
        knowledgeNoteMapper.update(note);
        boolean removed = knowledgeNoteMapper.deleteById(noteId) > 0;
        if (!removed) {
            return false;
        }

        // 3) 级联软删 AI 关联博客（仅当 note 曾写入 blogPostId）
        boolean cascadedBlog = false;
        if (blogPostId != null && noteBlogPublisher.isAvailable()) {
            noteBlogPublisher.softDelete(blogPostId, userId);
            cascadedBlog = true;
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sourceDocumentId", String.valueOf(sourceDocumentId));
        detail.put("detachedLeaf", "true");
        if (cascadedBlog) {
            detail.put("cascadedBlogPostId", String.valueOf(blogPostId));
        }
        opsAuditLogService.audit(
                OpsAuditActionConstant.READING_NOTE_DELETE,
                userId,
                OpsAuditActionConstant.RESOURCE_READING_NOTE,
                String.valueOf(noteId),
                true,
                detail,
                null);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearBlogLinkByPostId(Long blogPostId, Long userId) {
        if (blogPostId == null || userId == null) {
            return;
        }
        KnowledgeNote note = knowledgeNoteMapper.selectOneByQuery(
                QueryWrapper.create().eq("blog_post_id", blogPostId));
        if (note == null) {
            return;
        }
        // 校验归属：通过 source 的 userId
        SourceDocument source = sourceDocumentMapper.selectOneById(note.getSourceDocumentId());
        if (source == null || !Objects.equals(source.getUserId(), userId)) {
            return;
        }
        // Flex 默认 update 忽略 null，需 UpdateChain 才能清空 blog_post_id
        UpdateChain.of(KnowledgeNote.class)
                .set(KnowledgeNote::getBlogPostId, null)
                .set(KnowledgeNote::getPublishStatus, KnowledgeNoteConstant.PUBLISH_NOT_PUBLISHED)
                .set(KnowledgeNote::getUpdateTime, LocalDateTime.now())
                .where(KnowledgeNote::getId).eq(note.getId())
                .update();
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
        vo.setReviewStatus(note.getReviewStatus());
        vo.setIsArchived(note.getIsArchived());
        vo.setAlignmentScore(note.getAlignmentScore());
        vo.setAlignmentNote(note.getAlignmentNote());
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
