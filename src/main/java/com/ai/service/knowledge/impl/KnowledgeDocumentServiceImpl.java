package com.ai.service.knowledge.impl;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.config.knowledge.KnowledgeMinioProperties;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.knowledge.KnowledgeDocumentMapper;
import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.dto.knowledge.KnowledgeDocumentQueryRequest;
import com.ai.model.entity.knowledge.KnowledgeBase;
import com.ai.model.entity.knowledge.KnowledgeDocument;
import com.ai.model.entity.knowledge.SourceDocument;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.model.vo.knowledge.KnowledgeDownloadUrlVO;
import com.ai.model.vo.knowledge.KnowledgeTextChunk;
import com.ai.service.IntegrationCredentialsService;
import com.ai.service.OpsAuditLogService;
import com.ai.service.SiteSettingService;
import com.ai.service.knowledge.KnowledgeAiModelService;
import com.ai.service.knowledge.KnowledgeBaseService;
import com.ai.service.knowledge.KnowledgeChunkService;
import com.ai.service.knowledge.KnowledgeDocumentReaderService;
import com.ai.service.knowledge.KnowledgeDocumentService;
import com.ai.service.knowledge.KnowledgeStorageService;
import com.ai.service.knowledge.KnowledgeVectorStoreService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@ConditionalOnModule("knowledge")
@Service
public class KnowledgeDocumentServiceImpl extends ServiceImpl<KnowledgeDocumentMapper, KnowledgeDocument>
        implements KnowledgeDocumentService {

    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("pdf", "docx", "txt", "md", "markdown");

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeStorageService knowledgeStorageService;

    @Resource
    private KnowledgeMinioProperties minioProperties;

    @Resource
    private SiteSettingService siteSettingService;

    @Resource
    private IntegrationCredentialsService integrationCredentialsService;

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    @Resource
    private KnowledgeDocumentReaderService readerService;

    @Resource
    private KnowledgeChunkService chunkService;

    @Resource
    private KnowledgeAiModelService aiModelService;

    @Resource
    private KnowledgeVectorStoreService vectorStoreService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long userId) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.requireOwned(knowledgeBaseId, userId);
        validateFile(file);
        String ext = normalizeExt(FileUtil.extName(file.getOriginalFilename()));
        String objectKey = buildObjectKey(userId, knowledgeBaseId, ext);
        String bucket = integrationCredentialsService.minioBucketDocuments();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取上传文件失败");
        }
        // MinIO 远程 IO 不放进事务；落库失败时补偿删除已上传对象
        knowledgeStorageService.upload(bucket, objectKey, bytes, file.getContentType());
        KnowledgeDocument document;
        try {
            document = transactionTemplate.execute(status ->
                    doUploadPersist(knowledgeBase, file, userId, bucket, objectKey, ext));
        } catch (RuntimeException e) {
            try {
                knowledgeStorageService.delete(bucket, objectKey);
            } catch (Exception cleanupError) {
                log.warn("Rollback upload cleanup failed, bucket={}, key={}", bucket, objectKey, cleanupError);
            }
            throw e;
        }
        return toVO(document);
    }

    private KnowledgeDocument doUploadPersist(KnowledgeBase knowledgeBase, MultipartFile file, Long userId,
                                              String bucket, String objectKey, String ext) {
        String fileName = file.getOriginalFilename();
        LocalDateTime now = LocalDateTime.now();
        SourceDocument source = new SourceDocument();
        source.setTitle(fileName);
        source.setUserId(userId);
        source.setKnowledgeBaseId(knowledgeBase.getId());
        source.setSourceType("FILE");
        source.setBucketName(bucket);
        source.setObjectKey(objectKey);
        source.setStatus("PENDING");
        source.setCreateTime(now);
        source.setUpdateTime(now);
        source.setIsDelete(0);
        sourceDocumentMapper.insert(source);

        KnowledgeDocument document = new KnowledgeDocument();
        document.setKnowledgeBaseId(knowledgeBase.getId());
        document.setUserId(userId);
        document.setSourceDocumentId(source.getId());
        document.setFileName(fileName);
        document.setFileType(ext.toUpperCase());
        document.setFileSize(file.getSize());
        document.setBucketName(bucket);
        document.setObjectKey(objectKey);
        document.setParseStatus("UPLOADED");
        document.setChunkCount(0);
        document.setCreateTime(now);
        document.setUpdateTime(now);
        document.setIsDelete(0);
        this.save(document);

        source.setKnowledgeDocumentId(document.getId());
        sourceDocumentMapper.update(source);

        KnowledgeBase update = new KnowledgeBase();
        update.setId(knowledgeBase.getId());
        update.setDocumentCount(knowledgeBase.getDocumentCount() == null ? 1 : knowledgeBase.getDocumentCount() + 1);
        update.setUpdateTime(now);
        knowledgeBaseService.updateById(update);
        return document;
    }

    @Override
    public Page<KnowledgeDocumentVO> pageByKnowledgeBase(Long knowledgeBaseId, KnowledgeDocumentQueryRequest request, Long userId) {
        knowledgeBaseService.requireOwned(knowledgeBaseId, userId);
        int pageNum = request == null ? 1 : request.getPageNum();
        int pageSize = request == null ? 10 : request.getPageSize();
        String fileName = request == null ? null : request.getFileName();
        String fileType = request == null || StrUtil.isBlank(request.getFileType()) ? null : request.getFileType().toUpperCase();
        String parseStatus = request == null ? null : request.getParseStatus();
        QueryWrapper wrapper = QueryWrapper.create()
                .eq("knowledge_base_id", knowledgeBaseId)
                .eq("user_id", userId)
                .like("file_name", fileName, StrUtil.isNotBlank(fileName))
                .eq("file_type", fileType, StrUtil.isNotBlank(fileType))
                .eq("parse_status", parseStatus, StrUtil.isNotBlank(parseStatus))
                .orderBy("create_time", false);
        Page<KnowledgeDocument> page = this.page(Page.of(pageNum, pageSize), wrapper);
        List<KnowledgeDocumentVO> records = page.getRecords().stream().map(this::toVO).toList();
        Page<KnowledgeDocumentVO> voPage = new Page<>(page.getPageNumber(), page.getPageSize(), page.getTotalRow());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    public KnowledgeDocumentVO getVO(Long id, Long userId) {
        return toVO(requireOwned(id, userId));
    }

    @Override
    public boolean delete(Long id, Long userId) {
        KnowledgeDocument document = requireOwned(id, userId);
        // 先短事务软删本地（失败即整单回滚），外部存储在事务提交后再清理：
        // 反过来先删 PG/MinIO，一旦本地落库失败，向量与文件已被销毁且不可恢复
        Boolean removed = transactionTemplate.execute(status -> {
            boolean removedInner = this.removeById(document.getId());
            if (removedInner) {
                KnowledgeBase knowledgeBase = knowledgeBaseService.getById(document.getKnowledgeBaseId());
                if (knowledgeBase != null) {
                    int count = knowledgeBase.getDocumentCount() == null ? 0 : knowledgeBase.getDocumentCount();
                    KnowledgeBase update = new KnowledgeBase();
                    update.setId(knowledgeBase.getId());
                    update.setDocumentCount(Math.max(0, count - 1));
                    update.setUpdateTime(LocalDateTime.now());
                    knowledgeBaseService.updateById(update);
                }
                opsAuditLogService.audit(
                        OpsAuditActionConstant.KNOWLEDGE_DOCUMENT_DELETE,
                        userId,
                        OpsAuditActionConstant.RESOURCE_KNOWLEDGE_DOCUMENT,
                        String.valueOf(document.getId()),
                        true,
                        Map.of("knowledgeBaseId", document.getKnowledgeBaseId()),
                        null);
            }
            return removedInner;
        });
        if (Boolean.TRUE.equals(removed)) {
            cleanupExternalStores(document);
        }
        return Boolean.TRUE.equals(removed);
    }

    /**
     * 事务提交后的外部存储清理：best-effort，失败仅告警（软删数据可重入清理，残留记录 bucket/key 人工对账）。
     */
    private void cleanupExternalStores(KnowledgeDocument document) {
        try {
            vectorStoreService.deleteByDocumentId(document.getId());
        } catch (Exception e) {
            log.warn("Delete vectors failed after document delete, documentId={}", document.getId(), e);
        }
        try {
            knowledgeStorageService.delete(document.getBucketName(), document.getObjectKey());
        } catch (Exception e) {
            log.warn("Delete MinIO object failed after document delete, documentId={}, bucket={}, key={}",
                    document.getId(), document.getBucketName(), document.getObjectKey(), e);
        }
    }

    @Override
    public KnowledgeDownloadUrlVO getDownloadUrl(Long id, Long userId) {
        KnowledgeDocument document = requireOwned(id, userId);
        if ("knowledge-notes".equals(document.getBucketName())
                || (document.getObjectKey() != null && document.getObjectKey().startsWith("virtual/"))) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "精读虚拟文档不支持 MinIO 下载，请从精读工作台查看内容");
        }
        int expireSeconds = siteSettingService.getInt(
                SiteSettingConstant.MODULE_UPLOAD,
                "presign_expire_seconds",
                minioProperties.getPresignExpireSeconds());
        return new KnowledgeDownloadUrlVO(
                knowledgeStorageService.getPresignedUrl(document.getBucketName(), document.getObjectKey(), expireSeconds),
                expireSeconds
        );
    }

    @Override
    public KnowledgeDocumentVO parse(Long id, Long userId) {
        KnowledgeDocument document = requireOwned(id, userId);
        updateParseStatus(document.getId(), "PARSING", null, null, null);
        try {
            // MinIO 下载/向量写入均为外部 IO，不进事务；各状态更新独立提交，
            // 失败路径的 FAILED 状态因此能真正落库（原事务模式下会被回滚吞掉）
            byte[] bytes = knowledgeStorageService.download(document.getBucketName(), document.getObjectKey());
            String rawText = readerService.read(bytes, document.getFileType());
            List<KnowledgeTextChunk> chunks = chunkService.split(rawText);
            List<float[]> embeddings = aiModelService.embed(chunks.stream().map(KnowledgeTextChunk::content).toList());
            vectorStoreService.replaceChunks(document, chunks, embeddings);
            updateSourceRawText(document.getSourceDocumentId(), rawText, "SUCCESS", null);
            updateParseStatus(document.getId(), "PARSED", chunks.size(), null, LocalDateTime.now());
            return getVO(document.getId(), userId);
        } catch (BusinessException e) {
            updateParseStatus(document.getId(), "FAILED", null, e.getMessage(), null);
            updateSourceRawText(document.getSourceDocumentId(), null, "FAILED", e.getMessage());
            throw e;
        } catch (Exception e) {
            updateParseStatus(document.getId(), "FAILED", null, e.getMessage(), null);
            updateSourceRawText(document.getSourceDocumentId(), null, "FAILED", e.getMessage());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "文档解析失败：" + e.getMessage());
        }
    }

    @Override
    public List<KnowledgeChunkVO> listChunks(Long id, int pageNum, int pageSize, Long userId) {
        KnowledgeDocument document = requireOwned(id, userId);
        return vectorStoreService.listByDocument(document.getId(), pageNum, pageSize);
    }

    @Override
    public KnowledgeDocument requireOwned(Long id, Long userId) {
        KnowledgeDocument document = this.getById(id);
        if (document == null || !userId.equals(document.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "知识库文档不存在");
        }
        return document;
    }

    private void validateFile(MultipartFile file) {
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
    }

    private void updateParseStatus(Long id, String status, Integer chunkCount, String errorMessage, LocalDateTime parsedAt) {
        KnowledgeDocument update = new KnowledgeDocument();
        update.setId(id);
        update.setParseStatus(status);
        update.setChunkCount(chunkCount);
        update.setErrorMessage(errorMessage);
        update.setParsedAt(parsedAt);
        update.setUpdateTime(LocalDateTime.now());
        this.updateById(update);
    }

    private void updateSourceRawText(Long sourceDocumentId, String rawText, String status, String errorMsg) {
        if (sourceDocumentId == null) {
            return;
        }
        SourceDocument update = new SourceDocument();
        update.setId(sourceDocumentId);
        update.setRawText(rawText);
        update.setStatus(status);
        update.setErrorMsg(errorMsg);
        update.setUpdateTime(LocalDateTime.now());
        sourceDocumentMapper.update(update);
    }

    private String normalizeExt(String ext) {
        return StrUtil.blankToDefault(ext, "").toLowerCase();
    }

    private String buildObjectKey(Long userId, Long knowledgeBaseId, String ext) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "documents/%s/%s/%s/%s.%s".formatted(userId, knowledgeBaseId, date,
                UUID.randomUUID().toString().replace("-", ""), ext);
    }

    private KnowledgeDocumentVO toVO(KnowledgeDocument document) {
        KnowledgeDocumentVO vo = new KnowledgeDocumentVO();
        BeanUtil.copyProperties(document, vo);
        return vo;
    }
}
