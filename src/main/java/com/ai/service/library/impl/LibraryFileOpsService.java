package com.ai.service.library.impl;

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
import java.util.Comparator;
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

    // ---------- 查询 ----------

    public LibrarySummaryVO summary(Long userId) {
        List<LibraryFile> rows = core.listActiveDone(userId);
        LibrarySummaryVO vo = new LibrarySummaryVO();
        vo.setFileCount(rows.size());
        vo.setUsedBytes(rows.stream().mapToLong(f -> f.getSize() == null ? 0L : f.getSize()).sum());
        vo.setQuotaBytes(properties.getQuotaBytes());
        rows.stream()
                .filter(f -> f.getLastOpenedAt() != null)
                .max(Comparator.comparing(LibraryFile::getLastOpenedAt))
                .ifPresent(recent -> {
                    vo.setRecentFileId(recent.getId());
                    vo.setRecentFileAt(recent.getLastOpenedAt().format(LibraryFileCoreService.TIME_FORMAT));
                });
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
}
