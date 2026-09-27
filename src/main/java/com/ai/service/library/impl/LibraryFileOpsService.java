package com.ai.service.library.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.config.LibraryProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.library.LibraryCollectionMapper;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.mapper.library.LibraryTagMapper;
import com.ai.model.dto.library.LibraryCollectionCreateRequest;
import com.ai.model.dto.library.LibraryFileCollectionRemoveRequest;
import com.ai.model.dto.library.LibraryFileQueryRequest;
import com.ai.model.dto.library.LibraryFileRenameRequest;
import com.ai.model.dto.library.LibraryFileTagsRequest;
import com.ai.model.dto.library.LibraryFilesCollectionsRequest;
import com.ai.model.dto.library.LibraryTagCreateRequest;
import com.ai.model.dto.library.LibraryUploadCompleteRequest;
import com.ai.model.dto.library.LibraryUploadInitRequest;
import com.ai.model.dto.library.LibraryUsageAggRow;
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
import com.ai.service.library.LibraryStorageService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.ai.service.library.impl.LibraryFileCoreService.UPLOAD_DONE;
import static com.ai.service.library.impl.LibraryFileCoreService.UPLOAD_UPLOADING;

/**
 * 资料库日常操作：查询、标签/合集、上传、下载。
 */
@Slf4j
@Service
public class LibraryFileOpsService {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private static final int COLLECTION_NAME_MAX_LENGTH = 128;
    private static final int FILE_NAME_MAX_LENGTH = 512;

    @Resource
    private LibraryFileCoreService core;

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
    private LibrarySummaryCache summaryCache;

    // ---------- 查询 ----------

    public LibrarySummaryVO summary(Long userId) {
        // 读多写少的热路径：容量聚合与最近文件走两级缓存，容量写路径统一 evict
        return summaryCache.get(userId, () -> buildSummary(userId));
    }

    /** 缓存未命中时的即时组装：聚合口径与旧全量内存求和完全一致 */
    private LibrarySummaryVO buildSummary(Long userId) {
        LibraryUsageAggRow agg = libraryFileMapper.selectUsageAgg(userId);
        LibrarySummaryVO vo = new LibrarySummaryVO();
        vo.setFileCount(agg == null || agg.getFileCount() == null ? 0L : agg.getFileCount());
        vo.setUsedBytes(agg == null || agg.getUsedBytes() == null ? 0L : agg.getUsedBytes());
        vo.setQuotaBytes(properties.getQuotaBytes());
        // 最近打开文件：与旧实现「全量行内 max」等价；并列时取 id 较小者（对应旧扫描序首行胜出）
        LibraryFile recent = libraryFileMapper.selectOneByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .isNotNull("last_opened_at")
                .orderBy("last_opened_at", false)
                .orderBy("id", true)
                .limit(1));
        if (recent != null) {
            vo.setRecentFileId(recent.getId());
            vo.setRecentFileAt(recent.getLastOpenedAt().format(LibraryFileCoreService.TIME_FORMAT));
        }
        return vo;
    }

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
            if (LibraryFileCoreService.CATEGORIES.contains(category)) {
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
                    && core.parseIds(row.getTagIds()).stream().noneMatch(filterTagIds::contains)) {
                continue;
            }
            if (filterCollectionId != null && !core.parseIds(row.getCollectionIds()).contains(filterCollectionId)) {
                continue;
            }
            result.add(core.toVo(row));
        }
        return result;
    }

    public List<LibraryRecycleFileVO> listRecycle(Long userId) {
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .isNotNull("deleted_at")
                .orderBy("delete_time", false)
                .limit(LibraryFileCoreService.RECYCLE_LIST_LIMIT));
        List<LibraryRecycleFileVO> result = new ArrayList<>(rows.size());
        for (LibraryFile row : rows) {
            result.add(core.toRecycleVo(row));
        }
        return result;
    }

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

    public LibraryTagVO createTag(LibraryTagCreateRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名过长");
        }
        String color = request.getColor() == null ? "sakura" : request.getColor().trim();
        if (!LibraryFileCoreService.TAG_COLORS.contains(color)) {
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
        long used = core.usedBytes(userId);
        if (used + request.getSize() > properties.getQuotaBytes()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资料库容量不足，请先清理回收站或删除文件");
        }

        String ext = core.extractExt(request.getFileName());
        String category = core.categorize(ext);

        // 秒传：同用户下相同 SHA-256 的已完成文件且对象仍在，直接登记新条目
        LibraryFile dedup = libraryFileMapper.selectOneByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("sha256", sha256)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .orderBy("create_time", false)
                .limit(1));
        if (dedup != null && storage.statSize(properties.getBucket(), dedup.getObjectKey()) != null) {
            LibraryFile created = core.insertFile(userId, request.getFileName(), ext, category,
                    request.getSize(), sha256, dedup.getObjectKey(), UPLOAD_DONE);
            core.enqueueAiIfNeeded(created);
            // 秒传直接登记 DONE 行，容量即时变化，失效 summary 缓存
            summaryCache.evict(userId);
            LibraryUploadInitVO vo = new LibraryUploadInitVO();
            vo.setMode("instant");
            vo.setFile(core.toVo(created));
            return vo;
        }

        storage.ensureBucket(properties.getBucket());
        String objectKey = core.buildObjectKey(userId, sha256, ext);
        LibraryFile created = core.insertFile(userId, request.getFileName(), ext, category,
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

    public LibraryFileVO uploadComplete(LibraryUploadCompleteRequest request, Long userId) {
        LibraryFile file = core.requireOwned(request == null ? null : request.getFileId(), userId);
        if (UPLOAD_DONE.equals(file.getUploadStatus())) {
            return core.toVo(file);
        }
        Long actualSize = storage.statSize(properties.getBucket(), file.getObjectKey());
        if (actualSize == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未检测到已上传的文件，请先完成直传");
        }
        file.setSize(actualSize);
        file.setUploadStatus(UPLOAD_DONE);
        if (core.aiEligible(file.getExt())) {
            file.setAiStatus(LibraryFileCoreService.AI_PENDING);
        }
        file.setUpdateTime(LocalDateTime.now());
        libraryFileMapper.update(file);
        // 登记为 DONE 且写入真实大小，容量即时变化，失效 summary 缓存
        summaryCache.evict(userId);
        return core.toVo(file);
    }

    public LibraryFileVO uploadContent(Long fileId, Long userId, InputStream body, long contentLength,
                                       String contentType) {
        LibraryFile file = core.requireOwned(fileId, userId);
        if (UPLOAD_DONE.equals(file.getUploadStatus())) {
            return core.toVo(file);
        }
        if (body == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传内容不能为空");
        }
        storage.ensureBucket(properties.getBucket());
        long size = contentLength > 0 ? contentLength : (file.getSize() == null ? -1 : file.getSize());
        storage.put(properties.getBucket(), file.getObjectKey(), body, size, contentType);
        LibraryUploadCompleteRequest complete = new LibraryUploadCompleteRequest();
        complete.setFileId(fileId);
        return uploadComplete(complete, userId);
    }

    // ---------- 下载 / 打开 ----------

    public void streamDownload(Long fileId, Long userId, HttpServletResponse response) {
        LibraryFile file = core.requireActive(fileId, userId);
        if (!UPLOAD_DONE.equals(file.getUploadStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件尚未上传完成");
        }
        String name = StrUtil.blankToDefault(file.getFileName(), "file");
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setContentType(core.mimeOf(file.getExt()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded);
        if (file.getSize() != null && file.getSize() > 0) {
            response.setContentLengthLong(file.getSize());
        }
        core.markOpened(file);
        try {
            storage.writeTo(properties.getBucket(), file.getObjectKey(), response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.warn("Stream library download aborted, fileId={}", fileId, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "下载失败");
        }
    }

    public LibraryDownloadUrlVO downloadUrl(Long fileId, Long userId) {
        LibraryFile file = core.requireActive(fileId, userId);
        if (!UPLOAD_DONE.equals(file.getUploadStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件尚未上传完成");
        }
        LibraryDownloadUrlVO vo = new LibraryDownloadUrlVO();
        vo.setUrl(storage.presignedGetUrl(properties.getBucket(), file.getObjectKey(),
                properties.getPresignExpireSeconds()));
        vo.setExpiresIn(properties.getPresignExpireSeconds());
        core.markOpened(file);
        return vo;
    }

    public boolean open(Long fileId, Long userId) {
        LibraryFile file = core.requireActive(fileId, userId);
        core.markOpened(file);
        return true;
    }

    // ---------- 标签 / 合集 ----------

    public boolean updateFileTags(Long fileId, LibraryFileTagsRequest request, Long userId) {
        LibraryFile file = core.requireActive(fileId, userId);
        List<Long> tagIds = request == null || request.getTagIds() == null
                ? List.of()
                : request.getTagIds();
        LibraryFile patch = new LibraryFile();
        patch.setTagIds(tagIds.isEmpty() ? null : JSONUtil.toJsonStr(tagIds));
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
        return true;
    }

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
            LibraryFile file = core.requireActive(fileId, userId);
            List<Long> collectionIds = new ArrayList<>(core.parseIds(file.getCollectionIds()));
            if (!collectionIds.contains(collection.getId())) {
                collectionIds.add(collection.getId());
                LibraryFile patch = new LibraryFile();
                patch.setCollectionIds(JSONUtil.toJsonStr(collectionIds));
                libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
            }
        }
        return true;
    }

    // ---------- 编辑：合集 / 标签 / 文件重命名（§4.5 编辑类端点） ----------

    public LibraryCollectionVO createCollection(LibraryCollectionCreateRequest request, Long userId) {
        String name = requireCollectionName(request);
        String color = request.getColor() == null || request.getColor().isBlank()
                ? "blue"
                : request.getColor().trim();
        if (!LibraryFileCoreService.TAG_COLORS.contains(color)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的合集颜色: " + color);
        }
        Long existing = libraryCollectionMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("name", name));
        if (existing > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "合集已存在: " + name);
        }
        LocalDateTime now = LocalDateTime.now();
        LibraryCollection collection = new LibraryCollection();
        collection.setUserId(userId);
        collection.setName(name);
        collection.setColor(color);
        collection.setCreateTime(now);
        collection.setUpdateTime(now);
        libraryCollectionMapper.insert(collection);

        LibraryCollectionVO vo = new LibraryCollectionVO();
        vo.setId(collection.getId());
        vo.setName(collection.getName());
        vo.setColor(collection.getColor());
        return vo;
    }

    public boolean updateCollection(Long collectionId, LibraryCollectionCreateRequest request, Long userId) {
        LibraryCollection collection = requireCollection(collectionId, userId);
        String name = requireCollectionName(request);
        Long duplicate = libraryCollectionMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("name", name)
                .ne("id", collection.getId()));
        if (duplicate > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "合集已存在: " + name);
        }
        LibraryCollection patch = new LibraryCollection();
        patch.setName(name);
        if (request.getColor() != null && !request.getColor().isBlank()) {
            String color = request.getColor().trim();
            if (!LibraryFileCoreService.TAG_COLORS.contains(color)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的合集颜色: " + color);
            }
            patch.setColor(color);
        }
        patch.setUpdateTime(LocalDateTime.now());
        libraryCollectionMapper.updateByQuery(patch, QueryWrapper.create().eq("id", collection.getId()));
        return true;
    }

    /**
     * 删除合集：文件保留，仅解除文件上的归属关联（含回收站内文件，避免还原后残留死引用）。
     * 先剥离关联再删合集行：中途失败只是关联残留，合集仍在、可重试。
     */
    public boolean deleteCollection(Long collectionId, Long userId) {
        LibraryCollection collection = requireCollection(collectionId, userId);
        stripFileCollectionIds(userId, collection.getId());
        libraryCollectionMapper.deleteByQuery(QueryWrapper.create()
                .eq("id", collection.getId())
                .eq("user_id", userId));
        return true;
    }

    public LibraryTagVO updateTag(Long tagId, LibraryTagCreateRequest request, Long userId) {
        LibraryTag tag = requireTag(tagId, userId);
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签名过长");
        }
        Long duplicate = libraryTagMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("name", name)
                .ne("id", tag.getId()));
        if (duplicate > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标签已存在: " + name);
        }
        LibraryTag patch = new LibraryTag();
        patch.setName(name);
        String color = null;
        if (StrUtil.isNotBlank(request.getColor())) {
            color = request.getColor().trim();
            if (!LibraryFileCoreService.TAG_COLORS.contains(color)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的标签颜色: " + color);
            }
            patch.setColor(color);
        }
        patch.setUpdateTime(LocalDateTime.now());
        libraryTagMapper.updateByQuery(patch, QueryWrapper.create().eq("id", tag.getId()));

        LibraryTagVO vo = new LibraryTagVO();
        vo.setId(tag.getId());
        vo.setName(name);
        vo.setColor(color == null ? tag.getColor() : color);
        return vo;
    }

    /**
     * 删除标签：同步剥离所有文件上的该标签（文件挂的是标签 id，含回收站内文件）。
     * 先剥离关联再删标签行：中途失败只是关联残留，标签仍在、可重试。
     */
    public boolean deleteTag(Long tagId, Long userId) {
        LibraryTag tag = requireTag(tagId, userId);
        stripFileTagIds(userId, tag.getId());
        libraryTagMapper.deleteByQuery(QueryWrapper.create()
                .eq("id", tag.getId())
                .eq("user_id", userId));
        return true;
    }

    public boolean removeFileCollection(Long fileId, LibraryFileCollectionRemoveRequest request, Long userId) {
        LibraryFile file = core.requireActive(fileId, userId);
        if (request == null || request.getCollectionId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LibraryCollection collection = libraryCollectionMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", request.getCollectionId())
                .eq("user_id", userId));
        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "合集不存在");
        }
        List<Long> collectionIds = new ArrayList<>(core.parseIds(file.getCollectionIds()));
        // 幂等：文件本就不在该合集，视为已移除
        if (!collectionIds.remove(collection.getId())) {
            return true;
        }
        LibraryFile patch = new LibraryFile();
        patch.setCollectionIds(JSONUtil.toJsonStr(collectionIds));
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
        return true;
    }

    public LibraryFileVO renameFile(Long fileId, LibraryFileRenameRequest request, Long userId) {
        LibraryFile file = core.requireActive(fileId, userId);
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > FILE_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名过长");
        }
        Long duplicate = libraryFileMapper.selectCountByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("file_name", name)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at")
                .ne("id", file.getId()));
        if (duplicate > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "同名文件已存在: " + name);
        }
        // 对象键由 sha256+扩展名决定，重命名仅改 file_name，ext/分类保持不变
        LibraryFile patch = new LibraryFile();
        patch.setFileName(name);
        patch.setUpdateTime(LocalDateTime.now());
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
        file.setFileName(name);
        return core.toVo(file);
    }

    private String requireCollectionName(LibraryCollectionCreateRequest request) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "合集名不能为空");
        }
        String name = request.getName().trim();
        if (name.length() > COLLECTION_NAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "合集名过长");
        }
        return name;
    }

    private LibraryCollection requireCollection(Long collectionId, Long userId) {
        if (collectionId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LibraryCollection collection = libraryCollectionMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", collectionId)
                .eq("user_id", userId));
        if (collection == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "合集不存在");
        }
        return collection;
    }

    private LibraryTag requireTag(Long tagId, Long userId) {
        if (tagId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LibraryTag tag = libraryTagMapper.selectOneByQuery(QueryWrapper.create()
                .eq("id", tagId)
                .eq("user_id", userId));
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "标签不存在");
        }
        return tag;
    }

    /** 把合集 id 从该用户所有文件的 collection_ids JSON 中剥离；只投影 id 与 collection_ids，避免拉 ai_summary 等大字段 */
    private void stripFileCollectionIds(Long userId, Long collectionId) {
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .select("id", "collection_ids")
                .eq("user_id", userId));
        for (LibraryFile row : rows) {
            List<Long> collectionIds = core.parseIds(row.getCollectionIds());
            if (!collectionIds.contains(collectionId)) {
                continue;
            }
            LibraryFile patch = new LibraryFile();
            patch.setCollectionIds(JSONUtil.toJsonStr(collectionIds.stream()
                    .filter(id -> !collectionId.equals(id))
                    .toList()));
            libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", row.getId()));
        }
    }

    /** 把标签 id 从该用户所有文件的 tag_ids JSON 中剥离；只投影 id 与 tag_ids，避免拉 ai_summary 等大字段 */
    private void stripFileTagIds(Long userId, Long tagId) {
        List<LibraryFile> rows = libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .select("id", "tag_ids")
                .eq("user_id", userId));
        for (LibraryFile row : rows) {
            List<Long> tagIds = core.parseIds(row.getTagIds());
            if (!tagIds.contains(tagId)) {
                continue;
            }
            LibraryFile patch = new LibraryFile();
            patch.setTagIds(JSONUtil.toJsonStr(tagIds.stream()
                    .filter(id -> !tagId.equals(id))
                    .toList()));
            libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", row.getId()));
        }
    }
}
