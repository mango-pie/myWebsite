package com.ai.service.library.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.config.LibraryProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.library.LibraryCollectionMapper;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.mapper.library.LibraryTagMapper;
import com.ai.model.dto.library.LibraryFileQueryRequest;
import com.ai.model.dto.library.LibraryFileTagsRequest;
import com.ai.model.dto.library.LibraryFilesCollectionsRequest;
import com.ai.model.dto.library.LibraryIdsRequest;
import com.ai.model.dto.library.LibraryTagCreateRequest;
import com.ai.model.dto.library.LibraryUploadCompleteRequest;
import com.ai.model.dto.library.LibraryUploadInitRequest;
import com.ai.model.entity.library.LibraryCollection;
import com.ai.model.entity.library.LibraryFile;
import com.ai.model.entity.library.LibraryTag;
import com.ai.model.vo.library.LibraryCollectionVO;
import com.ai.model.vo.library.LibraryDownloadUrlVO;
import com.ai.model.vo.library.LibraryFileVO;
import com.ai.model.vo.library.LibraryRecycleFileVO;
import com.ai.model.vo.library.LibrarySummaryVO;
import com.ai.model.vo.library.LibraryTagVO;
import com.ai.model.vo.library.LibraryUploadInitVO;
import com.ai.service.ChatImageCaptionService;
import com.ai.service.library.LibraryStorageService;
import com.ai.service.library.LibraryService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeDistillationService;
import com.ai.service.knowledge.KnowledgeDocumentReaderService;
import com.ai.service.knowledge.KnowledgeIngestionService;
import com.ai.service.knowledge.KnowledgeNoteIndexService;
import com.ai.model.dto.knowledge.KnowledgeIngestFileRequest;
import com.ai.model.dto.knowledge.KnowledgeNoteIndexRequest;
import com.ai.model.entity.knowledge.KnowledgeNote;
import com.ai.model.entity.knowledge.SourceDocument;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateChain;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Slf4j
@ConditionalOnModule("library")
@Service
public class LibraryServiceImpl implements LibraryService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private static final String UPLOAD_UPLOADING = "UPLOADING";
    private static final String UPLOAD_DONE = "DONE";
    private static final String AI_NONE = "NONE";
    private static final String AI_PENDING = "PENDING";
    private static final String AI_DONE = "DONE";
    private static final String AI_FAILED = "FAILED";

    private static final Set<String> CATEGORIES = Set.of(
            "DOCUMENT", "IMAGE", "AUDIO", "VIDEO", "CODE", "ARCHIVE", "OTHER");

    private static final Set<String> TAG_COLORS =
            Set.of("sakura", "violet", "lilac", "blue", "mint", "sun");

    private static final Set<String> DOCUMENT_EXTS = Set.of(
            "pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx", "csv", "epub");
    private static final Set<String> CODE_EXTS = Set.of(
            "java", "ts", "tsx", "js", "jsx", "py", "go", "rs", "c", "cpp", "h", "cs", "kt",
            "scala", "rb", "php", "swift", "sh", "bat", "sql", "html", "css", "scss", "vue",
            "json", "xml", "yml", "yaml", "toml", "ini", "properties", "gradle");
    private static final Set<String> IMAGE_EXTS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg");
    private static final Set<String> AUDIO_EXTS = Set.of("mp3", "wav", "flac", "ogg", "m4a", "aac");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mkv", "avi", "mov", "webm");
    private static final Set<String> ARCHIVE_EXTS = Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz");

    /** AI 摘要覆盖的文本类扩展名（可直接按 UTF-8 截取送模型） */
    private static final Set<String> AI_TEXT_EXTS = Set.of(
            "txt", "md", "csv", "log",
            "java", "ts", "tsx", "js", "jsx", "py", "go", "rs", "c", "cpp", "h", "cs", "kt",
            "scala", "rb", "php", "swift", "sh", "bat", "sql", "html", "css", "scss", "vue",
            "json", "xml", "yml", "yaml", "toml", "ini", "properties", "gradle");

    /** 二进制文档类扩展名：经知识库文档解析器抽取文本后送模型 */
    private static final Set<String> AI_DOC_EXTS = Set.of("pdf", "docx");

    private static final int AI_TEXT_MAX_CHARS = 8000;
    private static final int RECYCLE_LIST_LIMIT = 1000;

    @Resource
    private LibraryFileMapper libraryFileMapper;

    @Resource
    private LibraryTagMapper libraryTagMapper;

    @Resource
    private LibraryCollectionMapper libraryCollectionMapper;

    @Resource
    private LibraryStorageService storage;

    @Resource
    private LibraryProperties properties;

    @Resource
    private ObjectProvider<KnowledgeAiModelService> aiModelProvider;

    @Resource
    private ObjectProvider<KnowledgeDocumentReaderService> docReaderProvider;

    @Resource
    private ObjectProvider<ChatImageCaptionService> imageCaptionProvider;

    @Resource
    private ObjectProvider<KnowledgeIngestionService> ingestionProvider;

    @Resource
    private ObjectProvider<KnowledgeDistillationService> distillProvider;

    @Resource
    private ObjectProvider<KnowledgeNoteIndexService> indexProvider;

    @Resource
    private LibraryVectorService vectorService;

    private final AtomicBoolean aiBusy = new AtomicBoolean(false);

    private final ExecutorService workerExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.SECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(64),
            r -> {
                Thread t = new Thread(r, "library-worker");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy
    public void shutdown() {
        workerExecutor.shutdown();
        try {
            if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- 查询 ----------

    @Override
    public LibrarySummaryVO summary(Long userId) {
        List<LibraryFile> rows = listActiveDone(userId);
        LibrarySummaryVO vo = new LibrarySummaryVO();
        vo.setFileCount(rows.size());
        vo.setUsedBytes(rows.stream().mapToLong(f -> f.getSize() == null ? 0L : f.getSize()).sum());
        vo.setQuotaBytes(properties.getQuotaBytes());
        rows.stream()
                .filter(f -> f.getLastOpenedAt() != null)
                .max(Comparator.comparing(LibraryFile::getLastOpenedAt))
                .ifPresent(recent -> {
                    vo.setRecentFileId(recent.getId());
                    vo.setRecentFileAt(recent.getLastOpenedAt().format(TIME_FORMAT));
                });
        return vo;
    }

    @Override
    public List<LibraryFileVO> listFiles(LibraryFileQueryRequest request, Long userId) {
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at");
        if (request != null && StrUtil.isNotBlank(request.getKeyword())) {
            wrapper.like("file_name", request.getKeyword().trim());
        }
        if (request != null && StrUtil.isNotBlank(request.getCategory())) {
            String category = request.getCategory().trim().toUpperCase();
            if (CATEGORIES.contains(category)) {
                wrapper.eq("category", category);
            }
        }
        String sort = request == null || StrUtil.isBlank(request.getSort()) ? "time" : request.getSort();
        boolean asc = request != null && "asc".equalsIgnoreCase(request.getDir());
        String column = switch (sort) {
            case "name" -> "file_name";
            case "size" -> "size";
            default -> "create_time";
        };
        wrapper.orderBy(column, asc);

        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(wrapper);
        List<Long> filterTagIds = request == null ? null : request.getTagIds();
        Long filterCollectionId = request == null ? null : request.getCollectionId();
        List<LibraryFileVO> result = new ArrayList<>(rows.size());
        for (LibraryFile row : rows) {
            if (filterTagIds != null && !filterTagIds.isEmpty()
                    && parseIds(row.getTagIds()).stream().noneMatch(filterTagIds::contains)) {
                continue;
            }
            if (filterCollectionId != null && !parseIds(row.getCollectionIds()).contains(filterCollectionId)) {
                continue;
            }
            result.add(toVo(row));
        }
        return result;
    }

    @Override
    public List<LibraryRecycleFileVO> listRecycle(Long userId) {
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .isNotNull("deleted_at")
                .orderBy("delete_time", false)
                .limit(RECYCLE_LIST_LIMIT));
        List<LibraryRecycleFileVO> result = new ArrayList<>(rows.size());
        for (LibraryFile row : rows) {
            result.add(toRecycleVo(row));
        }
        return result;
    }

    @Override
    public List<LibraryTagVO> listTags(Long userId) {
        List<LibraryTag> rows = libraryTagMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("create_time", true));
        List<LibraryTagVO> result = new ArrayList<>(rows.size());
        for (LibraryTag row : rows) {
            LibraryTagVO vo = new LibraryTagVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setColor(row.getColor());
            result.add(vo);
        }
        return result;
    }

    @Override
    public LibraryTagVO createTag(LibraryTagCreateRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名过长");
        }
        String color = request.getColor() == null ? "sakura" : request.getColor().trim();
        if (!TAG_COLORS.contains(color)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的标签颜色: " + color);
        }
        Long existing = libraryTagMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("name", name));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签已存在: " + name);
        }
        LocalDateTime now = LocalDateTime.now();
        LibraryTag tag = new LibraryTag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(color);
        tag.setCreateTime(now);
        tag.setUpdateTime(now);
        libraryTagMapper.insert(tag);

        LibraryTagVO vo = new LibraryTagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        return vo;
    }

    @Override
    public List<LibraryCollectionVO> listCollections(Long userId) {
        List<LibraryCollection> rows = libraryCollectionMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .orderBy("create_time", true));
        List<LibraryCollectionVO> result = new ArrayList<>(rows.size());
        for (LibraryCollection row : rows) {
            LibraryCollectionVO vo = new LibraryCollectionVO();
            vo.setId(row.getId());
            vo.setName(row.getName());
            vo.setColor(row.getColor());
            result.add(vo);
        }
        return result;
    }

    // ---------- 上传 ----------

    @Override
    public LibraryUploadInitVO uploadInit(LibraryUploadInitRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getFileName())
                || request.getSize() == null || request.getSize() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "fileName 与 size 不能为空");
        }
        if (request.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单文件大小超出上限");
        }
        String sha256 = request.getSha256() == null ? "" : request.getSha256().trim().toLowerCase();
        if (!SHA256_PATTERN.matcher(sha256).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "sha256 格式不合法");
        }
        long used = usedBytes(userId);
        if (used + request.getSize() > properties.getQuotaBytes()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资料库容量不足，请先清理回收站或删除文件");
        }

        String ext = extractExt(request.getFileName());
        String category = categorize(ext);

        // 秒传：同用户下相同 SHA-256 的已完成文件且对象仍在，直接登记新条目
        LibraryFile dedup = libraryFileMapper.selectOneByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("sha256", sha256)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .orderBy("create_time", false)
                .limit(1));
        if (dedup != null && storage.statSize(properties.getBucket(), dedup.getObjectKey()) != null) {
            LibraryFile created = insertFile(userId, request.getFileName(), ext, category,
                    request.getSize(), sha256, dedup.getObjectKey(), UPLOAD_DONE);
            enqueueAiIfNeeded(created);
            LibraryUploadInitVO vo = new LibraryUploadInitVO();
            vo.setMode("instant");
            vo.setFile(toVo(created));
            return vo;
        }

        storage.ensureBucket(properties.getBucket());
        String objectKey = buildObjectKey(userId, sha256, ext);
        LibraryFile created = insertFile(userId, request.getFileName(), ext, category,
                request.getSize(), sha256, objectKey, UPLOAD_UPLOADING);
        String uploadUrl = storage.presignedPutUrl(properties.getBucket(), objectKey,
                properties.getPresignExpireSeconds());

        LibraryUploadInitVO vo = new LibraryUploadInitVO();
        vo.setMode("direct");
        vo.setFileId(created.getId());
        vo.setUploadUrl(uploadUrl);
        vo.setObjectKey(objectKey);
        return vo;
    }

    @Override
    public LibraryFileVO uploadComplete(LibraryUploadCompleteRequest request, Long userId) {
        LibraryFile file = requireOwned(request == null ? null : request.getFileId(), userId);
        if (UPLOAD_DONE.equals(file.getUploadStatus())) {
            return toVo(file);
        }
        Long actualSize = storage.statSize(properties.getBucket(), file.getObjectKey());
        if (actualSize == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未检测到已上传的文件，请先完成直传");
        }
        file.setSize(actualSize);
        file.setUploadStatus(UPLOAD_DONE);
        if (aiEligible(file.getExt())) {
            file.setAiStatus(AI_PENDING);
        }
        file.setUpdateTime(LocalDateTime.now());
        libraryFileMapper.update(file);
        return toVo(file);
    }

    // ---------- 取出 ----------

    @Override
    public LibraryDownloadUrlVO downloadUrl(Long fileId, Long userId) {
        LibraryFile file = requireActive(fileId, userId);
        if (!UPLOAD_DONE.equals(file.getUploadStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件尚未上传完成");
        }
        LibraryDownloadUrlVO vo = new LibraryDownloadUrlVO();
        vo.setUrl(storage.presignedGetUrl(properties.getBucket(), file.getObjectKey(),
                properties.getPresignExpireSeconds()));
        vo.setExpiresIn(properties.getPresignExpireSeconds());
        markOpened(file);
        return vo;
    }

    @Override
    public boolean open(Long fileId, Long userId) {
        LibraryFile file = requireActive(fileId, userId);
        markOpened(file);
        return true;
    }

    private void markOpened(LibraryFile file) {
        LibraryFile patch = new LibraryFile();
        patch.setLastOpenedAt(LocalDateTime.now());
        patch.setOpenCount((file.getOpenCount() == null ? 0 : file.getOpenCount()) + 1);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
    }

    // ---------- 回收站 ----------

    @Override
    public boolean deleteFiles(LibraryIdsRequest request, Long userId) {
        List<Long> ids = requireIds(request);
        LibraryFile patch = new LibraryFile();
        patch.setDeletedAt(LocalDate.now());
        patch.setDeleteTime(LocalDateTime.now());
        return libraryFileMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_at")
                .in("id", ids)) > 0;
    }

    @Override
    public boolean restore(LibraryIdsRequest request, Long userId) {
        List<Long> ids = requireIds(request);
        return UpdateChain.of(LibraryFile.class)
                .set(LibraryFile::getDeletedAt, null)
                .set(LibraryFile::getDeleteTime, null)
                .where(LibraryFile::getUserId).eq(userId)
                .and(LibraryFile::getDeletedAt).isNotNull()
                .and(LibraryFile::getId).in(ids)
                .update();
    }

    @Override
    public boolean purge(LibraryIdsRequest request, Long userId) {
        List<Long> ids = requireIds(request);
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .isNotNull("deleted_at")
                .in("id", ids));
        if (rows.isEmpty()) {
            return false;
        }
        removeObjectsIfUnreferenced(userId, rows);
        for (LibraryFile row : rows) {
            vectorService.deleteByFileId(row.getId());
        }
        libraryFileMapper.deleteByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .in("id", rows.stream().map(LibraryFile::getId).toList()));
        return true;
    }

    @Override
    public int purgeExpiredRecycle() {
        LocalDate cutoff = LocalDate.now().minusDays(properties.getRecycleRetentionDays());
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .lt("deleted_at", cutoff)
                .limit(RECYCLE_LIST_LIMIT));
        if (rows.isEmpty()) {
            return 0;
        }
        for (LibraryFile row : rows) {
            removeObjectsIfUnreferenced(row.getUserId(), List.of(row));
        }
        libraryFileMapper.deleteByQuery(QueryWrapper.create()
                .lt("deleted_at", cutoff));
        log.info("Library recycle purge: removed {} expired files (cutoff={})", rows.size(), cutoff);
        return rows.size();
    }

    /** 同用户下同一对象键可能被秒传去重的多条记录共享，仅在其他引用不存在时删对象 */
    private void removeObjectsIfUnreferenced(Long userId, List<LibraryFile> removing) {
        List<Long> removingIds = removing.stream().map(LibraryFile::getId).toList();
        Set<String> keys = new LinkedHashSet<>();
        for (LibraryFile row : removing) {
            keys.add(row.getObjectKey());
        }
        for (String objectKey : keys) {
            long refs = libraryFileMapper.selectCountByQuery(QueryWrapper.create()
                    .eq("user_id", userId)
                    .eq("object_key", objectKey)
                    .notIn("id", removingIds));
            if (refs == 0) {
                storage.remove(properties.getBucket(), objectKey);
            }
        }
    }

    // ---------- 标签 / 合集 ----------

    @Override
    public boolean updateFileTags(Long fileId, LibraryFileTagsRequest request, Long userId) {
        LibraryFile file = requireActive(fileId, userId);
        List<Long> tagIds = request == null || request.getTagIds() == null
                ? List.of()
                : request.getTagIds();
        LibraryFile patch = new LibraryFile();
        patch.setTagIds(tagIds.isEmpty() ? null : JSONUtil.toJsonStr(tagIds));
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
        return true;
    }

    @Override
    public boolean assignCollections(LibraryFilesCollectionsRequest request, Long userId) {
        if (request == null || request.getCollectionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LibraryCollection collection = libraryCollectionMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", request.getCollectionId())
                .eq("user_id", userId));
        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "合集不存在");
        }
        List<Long> fileIds = request.getFileIds() == null ? List.of() : request.getFileIds();
        for (Long fileId : fileIds) {
            LibraryFile file = requireActive(fileId, userId);
            List<Long> collectionIds = new ArrayList<>(parseIds(file.getCollectionIds()));
            if (!collectionIds.contains(collection.getId())) {
                collectionIds.add(collection.getId());
                LibraryFile patch = new LibraryFile();
                patch.setCollectionIds(JSONUtil.toJsonStr(collectionIds));
                libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
            }
        }
        return true;
    }

    // ---------- AI 摘要与转知识库 ----------

    @Override
    public boolean regenerateAi(Long fileId, Long userId) {
        LibraryFile file = requireActive(fileId, userId);
        if (!aiEligible(file.getExt())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该文件类型暂不支持自动摘要");
        }
        return UpdateChain.of(LibraryFile.class)
                .set(LibraryFile::getAiStatus, AI_PENDING)
                .set(LibraryFile::getAiSummary, null)
                .set(LibraryFile::getAiError, null)
                .where(LibraryFile::getId).eq(file.getId())
                .update();
    }

    @Override
    public List<LibraryFileVO> semanticSearch(String keyword, Long userId, int limit) {
        if (StrUtil.isBlank(keyword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索词不能为空");
        }
        KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
        if (ai == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 服务未启用，语义搜索不可用");
        }
        int topK = Math.min(Math.max(limit, 1), 50);
        List<LibraryVectorService.VectorHit> hits =
                vectorService.search(userId, ai.embed(keyword.trim()), topK);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, Double> scoreById = new LinkedHashMap<>();
        for (LibraryVectorService.VectorHit hit : hits) {
            scoreById.put(hit.fileId(), hit.score());
        }
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .in("id", scoreById.keySet()));
        List<LibraryFileVO> result = new ArrayList<>(rows.size());
        for (LibraryFile row : rows) {
            LibraryFileVO vo = toVo(row);
            vo.setScore(scoreById.get(row.getId()));
            result.add(vo);
        }
        result.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return result.size() > topK ? result.subList(0, topK) : result;
    }

    @Override
    public int backfillEmbeddings() {
        KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
        if (ai == null || !vectorService.ping()) {
            return 0;
        }
        List<LibraryFile> candidates = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("ai_status", AI_DONE)
                .isNotNull("ai_summary")
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .orderBy("update_time", false)
                .limit(50));
        if (candidates.isEmpty()) {
            return 0;
        }
        List<Long> existing = vectorService.existingFileIds(
                candidates.stream().map(LibraryFile::getId).toList());
        int count = 0;
        for (LibraryFile file : candidates) {
            if (existing.contains(file.getId())) {
                continue;
            }
            embedFile(file, file.getAiSummary());
            count++;
            if (count >= 10) {
                break;
            }
        }
        return count;
    }

    @Override
    public void pollAiParseQueue() {
        if (aiBusy.get()) {
            return;
        }
        LibraryFile next = libraryFileMapper.selectOneByQuery(QueryWrapper.create()
                .eq("ai_status", AI_PENDING)
                .orderBy("update_time", true)
                .limit(1));
        if (next == null) {
            return;
        }
        if (!aiBusy.compareAndSet(false, true)) {
            return;
        }
        try {
            workerExecutor.execute(() -> {
                try {
                    runAiParse(next.getId());
                } finally {
                    aiBusy.set(false);
                }
            });
        } catch (RejectedExecutionException e) {
            aiBusy.set(false);
        }
    }

    private void runAiParse(Long fileId) {
        LibraryFile file = libraryFileMapper.selectOneById(fileId);
        if (file == null || !AI_PENDING.equals(file.getAiStatus())) {
            return;
        }
        try {
            String summary;
            if (IMAGE_EXTS.contains(file.getExt())) {
                ChatImageCaptionService caption = imageCaptionProvider.getIfAvailable();
                if (caption == null) {
                    markAiFailed(file, "图片描述服务未启用");
                    return;
                }
                byte[] bytes = storage.download(properties.getBucket(), file.getObjectKey());
                summary = caption.caption(bytes, mimeOf(file.getExt()));
            } else {
                KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
                if (ai == null) {
                    markAiFailed(file, "AI 服务未启用");
                    return;
                }
                byte[] bytes = storage.download(properties.getBucket(), file.getObjectKey());
                String text;
                if (AI_DOC_EXTS.contains(file.getExt())) {
                    KnowledgeDocumentReaderService reader = docReaderProvider.getIfAvailable();
                    if (reader == null) {
                        markAiFailed(file, "文档解析服务未启用（需开启知识库模块）");
                        return;
                    }
                    text = reader.read(bytes, file.getExt());
                } else {
                    text = new String(bytes, StandardCharsets.UTF_8);
                }
                if (text.length() > AI_TEXT_MAX_CHARS) {
                    text = text.substring(0, AI_TEXT_MAX_CHARS);
                }
                String prompt = """
                        请为以下文件内容生成不超过 120 字的中文摘要，直接输出摘要正文，不要任何前缀。

                        文件名：%s

                        内容：
                        %s
                        """.formatted(file.getFileName(), text);
                summary = ai.chat(prompt);
            }
            markAiDone(file, summary);
            embedFile(file, summary);
        } catch (Exception e) {
            log.warn("Library AI parse failed, fileId={}: {}", fileId, e.getMessage());
            markAiFailed(file, StrUtil.maxLength(e.getMessage(), 500));
        }
    }

    /** 摘要完成后写入语义检索向量；失败仅记日志，不影响摘要状态 */
    private void embedFile(LibraryFile file, String summary) {
        try {
            KnowledgeAiModelService ai = aiModelProvider.getIfAvailable();
            if (ai == null || StrUtil.isBlank(summary)) {
                return;
            }
            String content = StrUtil.blankToDefault(file.getFileName(), "") + "\n" + summary;
            vectorService.upsert(file.getUserId(), file.getId(), content, ai.embed(content));
        } catch (Exception e) {
            log.warn("Library embedding failed, fileId={}: {}", file.getId(), e.getMessage());
        }
    }

    private void markAiDone(LibraryFile file, String summary) {
        LibraryFile patch = new LibraryFile();
        patch.setAiStatus(AI_DONE);
        patch.setAiSummary(summary);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("id", file.getId())
                .eq("ai_status", AI_PENDING));
    }

    private void markAiFailed(LibraryFile file, String error) {
        LibraryFile patch = new LibraryFile();
        patch.setAiStatus(AI_FAILED);
        patch.setAiError(error);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create()
                .eq("id", file.getId())
                .eq("ai_status", AI_PENDING));
    }

    @Override
    public boolean transferToKnowledge(Long fileId, Long userId) {
        LibraryFile file = requireActive(fileId, userId);
        if (ingestionProvider.getIfAvailable() == null || distillProvider.getIfAvailable() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "知识库模块未启用，无法转入");
        }
        try {
            workerExecutor.execute(() -> doTransfer(file.getId(), userId));
        } catch (RejectedExecutionException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "后台处理队列已满，请稍后再试");
        }
        return true;
    }

    private void doTransfer(Long fileId, Long userId) {
        try {
            LibraryFile file = libraryFileMapper.selectOneById(fileId);
            if (file == null) {
                return;
            }
            KnowledgeIngestionService ingestion = ingestionProvider.getIfAvailable();
            KnowledgeDistillationService distill = distillProvider.getIfAvailable();
            KnowledgeNoteIndexService index = indexProvider.getIfAvailable();
            if (ingestion == null || distill == null) {
                log.warn("Library transfer skipped, knowledge module disabled, fileId={}", fileId);
                return;
            }
            byte[] content = storage.download(properties.getBucket(), file.getObjectKey());
            String tags = resolveTagNames(file);
            KnowledgeIngestFileRequest request = new KnowledgeIngestFileRequest();
            request.setTitle(StrUtil.blankToDefault(stripExt(file.getFileName()), file.getFileName()));
            request.setTags(tags);
            SourceDocument source = ingestion.ingestFile(
                    toMultipartFile(file, content), request, userId);
            KnowledgeNote note = distill.distillToMarkdown(source.getId(), userId, tags);
            if (index != null) {
                index.indexToKnowledgeBase(note.getId(), new KnowledgeNoteIndexRequest(), userId);
            }
            log.info("Library file transferred to knowledge, fileId={}, noteId={}", fileId, note.getId());
        } catch (Exception e) {
            log.error("Library transfer-knowledge failed, fileId={}", fileId, e);
        }
    }

    private String resolveTagNames(LibraryFile file) {
        List<Long> tagIds = parseIds(file.getTagIds());
        if (tagIds.isEmpty()) {
            return null;
        }
        List<String> names = new ArrayList<>();
        for (LibraryTag tag : libraryTagMapper.selectListByIds(tagIds)) {
            names.add(tag.getName());
        }
        return names.isEmpty() ? null : String.join(",", names);
    }

    private MultipartFile toMultipartFile(LibraryFile file, byte[] content) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return file.getFileName();
            }

            @Override
            public String getContentType() {
                return mimeOf(file.getExt());
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

    // ---------- 内部工具 ----------

    private LibraryFile insertFile(Long userId, String fileName, String ext, String category,
                                   long size, String sha256, String objectKey, String uploadStatus) {
        LocalDateTime now = LocalDateTime.now();
        LibraryFile file = new LibraryFile();
        file.setUserId(userId);
        file.setFileName(fileName.trim());
        file.setExt(ext);
        file.setCategory(category);
        file.setSize(size);
        file.setSha256(sha256);
        file.setObjectKey(objectKey);
        file.setUploadStatus(uploadStatus);
        file.setAiStatus(AI_NONE);
        file.setOpenCount(0);
        file.setCreateTime(now);
        file.setUpdateTime(now);
        libraryFileMapper.insert(file);
        return file;
    }

    private void enqueueAiIfNeeded(LibraryFile file) {
        if (!aiEligible(file.getExt())) {
            return;
        }
        LibraryFile patch = new LibraryFile();
        patch.setAiStatus(AI_PENDING);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
    }

    private boolean aiEligible(String ext) {
        return AI_TEXT_EXTS.contains(ext) || AI_DOC_EXTS.contains(ext) || IMAGE_EXTS.contains(ext);
    }

    private long usedBytes(Long userId) {
        return listActiveDone(userId).stream()
                .mapToLong(f -> f.getSize() == null ? 0L : f.getSize())
                .sum();
    }

    private List<LibraryFile> listActiveDone(Long userId) {
        return libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at"));
    }

    private LibraryFile requireOwned(Long fileId, Long userId) {
        if (fileId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LibraryFile file = libraryFileMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", fileId)
                .eq("user_id", userId));
        if (file == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文件不存在");
        }
        return file;
    }

    private LibraryFile requireActive(Long fileId, Long userId) {
        LibraryFile file = requireOwned(fileId, userId);
        if (file.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件已在回收站");
        }
        return file;
    }

    private List<Long> requireIds(LibraryIdsRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ids 不能为空");
        }
        return request.getIds();
    }

    private String extractExt(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "").trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    private String stripExt(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "").trim();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private String categorize(String ext) {
        if (DOCUMENT_EXTS.contains(ext)) {
            return "DOCUMENT";
        }
        if (IMAGE_EXTS.contains(ext)) {
            return "IMAGE";
        }
        if (AUDIO_EXTS.contains(ext)) {
            return "AUDIO";
        }
        if (VIDEO_EXTS.contains(ext)) {
            return "VIDEO";
        }
        if (CODE_EXTS.contains(ext)) {
            return "CODE";
        }
        if (ARCHIVE_EXTS.contains(ext)) {
            return "ARCHIVE";
        }
        return "OTHER";
    }

    private String buildObjectKey(Long userId, String sha256, String ext) {
        String month = DateTimeFormatter.ofPattern("yyyyMM").format(LocalDate.now());
        return ext.isBlank()
                ? "library/%d/%s/%s".formatted(userId, month, sha256)
                : "library/%d/%s/%s.%s".formatted(userId, month, sha256, ext);
    }

    private String mimeOf(String ext) {
        return switch (ext) {
            case "md" -> "text/markdown";
            case "txt", "log" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "css" -> "text/css";
            case "js", "mjs" -> "text/javascript";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "mp3" -> "audio/mpeg";
            case "wav" -> "audio/wav";
            case "mp4" -> "video/mp4";
            case "webm" -> "video/webm";
            case "zip" -> "application/zip";
            default -> "application/octet-stream";
        };
    }

    private List<Long> parseIds(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, Long.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private LibraryFileVO toVo(LibraryFile file) {
        LibraryFileVO vo = new LibraryFileVO();
        vo.setId(file.getId());
        vo.setName(file.getFileName());
        vo.setExt(file.getExt());
        vo.setCategory(file.getCategory());
        vo.setSize(file.getSize());
        LocalDateTime uploadedAt = file.getCreateTime() == null ? file.getUpdateTime() : file.getCreateTime();
        vo.setUploadedAt(uploadedAt == null ? null : uploadedAt.format(TIME_FORMAT));
        vo.setTagIds(new ArrayList<>(parseIds(file.getTagIds())));
        vo.setCollectionIds(new ArrayList<>(parseIds(file.getCollectionIds())));
        vo.setAiStatus(file.getAiStatus());
        vo.setAiSummary(file.getAiSummary());
        vo.setSha256(file.getSha256());
        vo.setObjectKey(file.getObjectKey());
        return vo;
    }

    private LibraryRecycleFileVO toRecycleVo(LibraryFile file) {
        LibraryRecycleFileVO vo = new LibraryRecycleFileVO();
        vo.setId(file.getId());
        vo.setName(file.getFileName());
        vo.setExt(file.getExt());
        vo.setCategory(file.getCategory());
        vo.setSize(file.getSize());
        LocalDateTime uploadedAt = file.getCreateTime() == null ? file.getUpdateTime() : file.getCreateTime();
        vo.setUploadedAt(uploadedAt == null ? null : uploadedAt.format(TIME_FORMAT));
        vo.setTagIds(parseIds(file.getTagIds()));
        vo.setCollectionIds(parseIds(file.getCollectionIds()));
        vo.setAiStatus(file.getAiStatus());
        vo.setAiSummary(file.getAiSummary());
        vo.setSha256(file.getSha256());
        vo.setObjectKey(file.getObjectKey());
        vo.setDeletedAt(file.getDeletedAt() == null ? null : file.getDeletedAt().format(DATE_FORMAT));
        return vo;
    }
}
