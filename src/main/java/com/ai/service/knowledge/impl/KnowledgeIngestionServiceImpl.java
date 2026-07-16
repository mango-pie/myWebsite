package com.ai.service.knowledge.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.BizStatMetricConstant;
import com.ai.constant.knowledge.KnowledgeNoteConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeIngestFileRequest;
import com.ai.model.dto.knowledge.KnowledgeIngestUrlRequest;
import com.ai.model.dto.knowledge.KnowledgeProcessRequest;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.service.BizStatDailyService;
import com.ai.service.knowledge.ContentExtractor;
import com.ai.service.knowledge.KnowledgeDocumentReaderService;
import com.ai.service.knowledge.KnowledgeIngestionService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class KnowledgeIngestionServiceImpl implements KnowledgeIngestionService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "docx", "txt", "md", "markdown");

    @Resource
    private List<ContentExtractor> contentExtractors;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private KnowledgeDocumentReaderService readerService;

    @Resource
    private BizStatDailyService bizStatDailyService;

    @Override
    public SourceDocument ingestUrl(KnowledgeProcessRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 不能为空");
        }
        KnowledgeIngestUrlRequest urlRequest = new KnowledgeIngestUrlRequest();
        urlRequest.setUrl(request.getUrl());
        urlRequest.setTitle(request.getTitle());
        urlRequest.setTags(request.getTags());
        urlRequest.setSourceType(KnowledgeNoteConstant.SOURCE_URL);
        SourceDocument source = ingestUrl(urlRequest, userId);
        if (request.getKnowledgeBaseId() != null) {
            source.setKnowledgeBaseId(request.getKnowledgeBaseId());
            sourceDocumentMapper.update(source);
        }
        return source;
    }

    @Override
    public SourceDocument ingestUrl(KnowledgeIngestUrlRequest request, Long userId) {
        if (request == null || StrUtil.isBlank(request.getUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "URL 不能为空");
        }
        String sourceType = StrUtil.blankToDefault(request.getSourceType(), KnowledgeNoteConstant.SOURCE_URL).toUpperCase();
        if (!KnowledgeNoteConstant.SOURCE_URL.equals(sourceType) && !KnowledgeNoteConstant.SOURCE_AGENT.equals(sourceType)) {
            sourceType = KnowledgeNoteConstant.SOURCE_URL;
        }
        String rawText = contentExtractors.stream()
                .filter(extractor -> extractor.supports("URL"))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.OPERATION_ERROR, "URL 抓取器未配置"))
                .extract(request.getUrl());
        return createSource(
                StrUtil.blankToDefault(request.getTitle(), request.getUrl()),
                sourceType,
                request.getUrl(),
                rawText,
                null,
                null,
                null,
                userId
        );
    }

    @Override
    public SourceDocument ingestFile(MultipartFile file, KnowledgeIngestFileRequest request, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单个文件不能超过 50MB");
        }
        String ext = normalizeExt(FileUtil.extName(file.getOriginalFilename()));
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "仅支持 PDF、DOCX、TXT、Markdown 文件");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取上传文件失败");
        }
        String rawText = readerService.read(bytes, ext.toUpperCase());
        if (StrUtil.isBlank(rawText)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未能从文件中提取到文本");
        }
        String title = request != null && StrUtil.isNotBlank(request.getTitle())
                ? request.getTitle()
                : StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        String objectKey = "virtual/" + userId + "/" + UUID.randomUUID() + "." + ext;
        return createSource(
                title,
                KnowledgeNoteConstant.SOURCE_FILE,
                null,
                rawText,
                null,
                KnowledgeNoteConstant.VIRTUAL_BUCKET,
                objectKey,
                userId
        );
    }

    @Override
    public SourceDocument ingestAgentResult(String title, String url, String rawText, Long knowledgeBaseId, Long userId) {
        return createSource(title, KnowledgeNoteConstant.SOURCE_AGENT, url, rawText, knowledgeBaseId, null, null, userId);
    }

    private SourceDocument createSource(String title,
                                        String type,
                                        String url,
                                        String rawText,
                                        Long knowledgeBaseId,
                                        String bucketName,
                                        String objectKey,
                                        Long userId) {
        LocalDateTime now = LocalDateTime.now();
        SourceDocument source = new SourceDocument();
        source.setTitle(title);
        source.setUserId(userId);
        source.setKnowledgeBaseId(knowledgeBaseId);
        source.setSourceType(type);
        source.setSourceUrl(url);
        source.setBucketName(bucketName);
        source.setObjectKey(objectKey);
        source.setRawText(rawText);
        source.setStatus("SUCCESS");
        source.setCreateTime(now);
        source.setUpdateTime(now);
        source.setIsDelete(0);
        sourceDocumentMapper.insert(source);
        bizStatDailyService.increment(BizStatMetricConstant.READING_INGEST_SUCCESS, 1);
        return source;
    }

    private String normalizeExt(String ext) {
        if (StrUtil.isBlank(ext)) {
            return "";
        }
        String normalized = ext.toLowerCase();
        return "markdown".equals(normalized) ? "md" : normalized;
    }
}
