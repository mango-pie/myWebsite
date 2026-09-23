package com.ai.service.library.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.config.LibraryProperties;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.library.LibraryFileMapper;
import com.ai.model.dto.library.LibraryIdsRequest;
import com.ai.model.entity.library.LibraryFile;
import com.ai.model.vo.library.LibraryFileVO;
import com.ai.model.vo.library.LibraryRecycleFileVO;
import com.ai.service.library.LibraryStorageService;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 资料库共享内核：文件行读写、公共校验与 VO 组装。
 * 常量与工具供 FileOps / Recycle / AiOrchestration 三个协作类复用。
 */
@Service
public class LibraryFileCoreService {

    public static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    public static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public static final String UPLOAD_UPLOADING = "UPLOADING";
    public static final String UPLOAD_DONE = "DONE";
    public static final String AI_NONE = "NONE";
    public static final String AI_PENDING = "PENDING";
    public static final String AI_DONE = "DONE";
    public static final String AI_FAILED = "FAILED";

    public static final Set<String> CATEGORIES = Set.of(
            "DOCUMENT", "IMAGE", "AUDIO", "VIDEO", "CODE", "ARCHIVE", "OTHER");

    public static final Set<String> TAG_COLORS =
            Set.of("sakura", "violet", "lilac", "blue", "mint", "sun");

    public static final Set<String> DOCUMENT_EXTS = Set.of(
            "pdf", "doc", "docx", "txt", "md", "xls", "xlsx", "ppt", "pptx", "csv", "epub");
    public static final Set<String> CODE_EXTS = Set.of(
            "java", "ts", "tsx", "js", "jsx", "py", "go", "rs", "c", "cpp", "h", "cs", "kt",
            "scala", "rb", "php", "swift", "sh", "bat", "sql", "html", "css", "scss", "vue",
            "json", "xml", "yml", "yaml", "toml", "ini", "properties", "gradle");
    public static final Set<String> IMAGE_EXTS = Set.of(
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg");
    public static final Set<String> AUDIO_EXTS = Set.of("mp3", "wav", "flac", "ogg", "m4a", "aac");
    public static final Set<String> VIDEO_EXTS = Set.of("mp4", "mkv", "avi", "mov", "webm");
    public static final Set<String> ARCHIVE_EXTS = Set.of("zip", "rar", "7z", "tar", "gz", "bz2", "xz");

    /** AI 摘要覆盖的文本类扩展名（可直接按 UTF-8 截取送模型） */
    public static final Set<String> AI_TEXT_EXTS = Set.of(
            "txt", "md", "csv", "log",
            "java", "ts", "tsx", "js", "jsx", "py", "go", "rs", "c", "cpp", "h", "cs", "kt",
            "scala", "rb", "php", "swift", "sh", "bat", "sql", "html", "css", "scss", "vue",
            "json", "xml", "yml", "yaml", "toml", "ini", "properties", "gradle");

    /** 二进制文档类扩展名：经知识库文档解析器抽取文本后送模型 */
    public static final Set<String> AI_DOC_EXTS = Set.of("pdf", "docx");

    public static final int AI_TEXT_MAX_CHARS = 8000;
    public static final int RECYCLE_LIST_LIMIT = 1000;

    @Resource
    private LibraryFileMapper libraryFileMapper;

    public LibraryFile requireOwned(Long fileId, Long userId) {
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

    public LibraryFile requireActive(Long fileId, Long userId) {
        LibraryFile file = requireOwned(fileId, userId);
        if (file.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件已在回收站");
        }
        return file;
    }

    public void markOpened(LibraryFile file) {
        LibraryFile patch = new LibraryFile();
        patch.setLastOpenedAt(LocalDateTime.now());
        patch.setOpenCount((file.getOpenCount() == null ? 0 : file.getOpenCount()) + 1);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
    }

    public LibraryFile insertFile(Long userId, String fileName, String ext, String category,
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

    public void enqueueAiIfNeeded(LibraryFile file) {
        if (!aiEligible(file.getExt())) {
            return;
        }
        LibraryFile patch = new LibraryFile();
        patch.setAiStatus(AI_PENDING);
        libraryFileMapper.updateByQuery(patch, QueryWrapper.create().eq("id", file.getId()));
    }

    public boolean aiEligible(String ext) {
        return AI_TEXT_EXTS.contains(ext) || AI_DOC_EXTS.contains(ext) || IMAGE_EXTS.contains(ext);
    }

    public long usedBytes(Long userId) {
        return listActiveDone(userId).stream()
                .mapToLong(f -> f.getSize() == null ? 0L : f.getSize())
                .sum();
    }

    public List<LibraryFile> listActiveDone(Long userId) {
        return libraryFileMapper.selectListByQuery(QueryWrapper.create()
                .eq("user_id", userId)
                .eq("upload_status", UPLOAD_DONE)
                .isNull("deleted_at"));
    }

    public List<Long> requireIds(LibraryIdsRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ids 不能为空");
        }
        return request.getIds();
    }

    public String extractExt(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "").trim();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase();
    }

    public String stripExt(String fileName) {
        String name = StrUtil.blankToDefault(fileName, "").trim();
        int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    public String categorize(String ext) {
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

    public String buildObjectKey(Long userId, String sha256, String ext) {
        String month = DateTimeFormatter.ofPattern("yyyyMM").format(LocalDate.now());
        return ext.isBlank()
                ? "library/%d/%s/%s".formatted(userId, month, sha256)
                : "library/%d/%s/%s.%s".formatted(userId, month, sha256, ext);
    }

    public String mimeOf(String ext) {
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

    public List<Long> parseIds(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(json, Long.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    public LibraryFileVO toVo(LibraryFile file) {
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

    public LibraryRecycleFileVO toRecycleVo(LibraryFile file) {
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
