package com.ai.service.library.impl;

import com.ai.config.ConditionalOnModule;

import com.ai.model.dto.library.LibraryCollectionCreateRequest;
import com.ai.model.dto.library.LibraryFileCollectionRemoveRequest;
import com.ai.model.dto.library.LibraryFileQueryRequest;
import com.ai.model.dto.library.LibraryFileRenameRequest;
import com.ai.model.dto.library.LibraryFileTagsRequest;
import com.ai.model.dto.library.LibraryFilesCollectionsRequest;
import com.ai.model.dto.library.LibraryIdsRequest;
import com.ai.model.dto.library.LibraryTagCreateRequest;
import com.ai.model.dto.library.LibraryUploadCompleteRequest;
import com.ai.model.dto.library.LibraryUploadInitRequest;
import com.ai.model.vo.library.LibraryCollectionVO;
import com.ai.model.vo.library.LibraryDownloadUrlVO;
import com.ai.model.vo.library.LibraryFileVO;
import com.ai.model.vo.library.LibraryRecycleFileVO;
import com.ai.model.vo.library.LibrarySummaryVO;
import com.ai.model.vo.library.LibraryTagVO;
import com.ai.model.vo.library.LibraryUploadInitVO;
import com.ai.service.library.LibraryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * 资料库对外门面：按职责委托给四个协作类——
 * {@link LibraryFileOpsService}（日常操作）、{@link LibraryRecycleService}（回收站）、
 * {@link LibraryAiOrchestrationService}（AI 摘要/语义/转知识库）。
 * 对 Controller 与定时任务保持原有 LibraryService 契约不变。
 */
@ConditionalOnModule("library")
@Service
public class LibraryServiceImpl implements LibraryService {

    @Resource
    private LibraryFileOpsService fileOpsService;

    @Resource
    private LibraryRecycleService recycleService;

    @Resource
    private LibraryAiOrchestrationService aiOrchestrationService;

    // ---------- 查询 / 标签 / 合集 ----------

    @Override
    public LibrarySummaryVO summary(Long userId) {
        return fileOpsService.summary(userId);
    }

    @Override
    public List<LibraryFileVO> listFiles(LibraryFileQueryRequest request, Long userId) {
        return fileOpsService.listFiles(request, userId);
    }

    @Override
    public List<LibraryRecycleFileVO> listRecycle(Long userId) {
        return fileOpsService.listRecycle(userId);
    }

    @Override
    public List<LibraryTagVO> listTags(Long userId) {
        return fileOpsService.listTags(userId);
    }

    @Override
    public LibraryTagVO createTag(LibraryTagCreateRequest request, Long userId) {
        return fileOpsService.createTag(request, userId);
    }

    @Override
    public List<LibraryCollectionVO> listCollections(Long userId) {
        return fileOpsService.listCollections(userId);
    }

    @Override
    public LibraryCollectionVO createCollection(LibraryCollectionCreateRequest request, Long userId) {
        return fileOpsService.createCollection(request, userId);
    }

    @Override
    public boolean updateCollection(Long collectionId, LibraryCollectionCreateRequest request, Long userId) {
        return fileOpsService.updateCollection(collectionId, request, userId);
    }

    @Override
    public boolean deleteCollection(Long collectionId, Long userId) {
        return fileOpsService.deleteCollection(collectionId, userId);
    }

    @Override
    public LibraryTagVO updateTag(Long tagId, LibraryTagCreateRequest request, Long userId) {
        return fileOpsService.updateTag(tagId, request, userId);
    }

    @Override
    public boolean deleteTag(Long tagId, Long userId) {
        return fileOpsService.deleteTag(tagId, userId);
    }

    @Override
    public boolean removeFileCollection(Long fileId, LibraryFileCollectionRemoveRequest request, Long userId) {
        return fileOpsService.removeFileCollection(fileId, request, userId);
    }

    @Override
    public LibraryFileVO renameFile(Long fileId, LibraryFileRenameRequest request, Long userId) {
        return fileOpsService.renameFile(fileId, request, userId);
    }

    // ---------- 上传 / 下载 ----------

    @Override
    public LibraryUploadInitVO uploadInit(LibraryUploadInitRequest request, Long userId) {
        return fileOpsService.uploadInit(request, userId);
    }

    @Override
    public LibraryFileVO uploadComplete(LibraryUploadCompleteRequest request, Long userId) {
        return fileOpsService.uploadComplete(request, userId);
    }

    @Override
    public LibraryFileVO uploadContent(Long fileId, Long userId, InputStream body, long contentLength,
                                       String contentType) {
        return fileOpsService.uploadContent(fileId, userId, body, contentLength, contentType);
    }

    @Override
    public void streamDownload(Long fileId, Long userId, HttpServletResponse response) {
        fileOpsService.streamDownload(fileId, userId, response);
    }

    @Override
    public LibraryDownloadUrlVO downloadUrl(Long fileId, Long userId) {
        return fileOpsService.downloadUrl(fileId, userId);
    }

    @Override
    public boolean open(Long fileId, Long userId) {
        return fileOpsService.open(fileId, userId);
    }

    // ---------- 标签 / 合集维护 ----------

    @Override
    public boolean updateFileTags(Long fileId, LibraryFileTagsRequest request, Long userId) {
        return fileOpsService.updateFileTags(fileId, request, userId);
    }

    @Override
    public boolean assignCollections(LibraryFilesCollectionsRequest request, Long userId) {
        return fileOpsService.assignCollections(request, userId);
    }

    // ---------- 回收站 ----------

    @Override
    public boolean deleteFiles(LibraryIdsRequest request, Long userId) {
        return recycleService.deleteFiles(request, userId);
    }

    @Override
    public boolean restore(LibraryIdsRequest request, Long userId) {
        return recycleService.restore(request, userId);
    }

    @Override
    public boolean purge(LibraryIdsRequest request, Long userId) {
        return recycleService.purge(request, userId);
    }

    @Override
    public int purgeExpiredRecycle() {
        return recycleService.purgeExpiredRecycle();
    }

    // ---------- AI 摘要 / 语义 / 转知识库 ----------

    @Override
    public boolean regenerateAi(Long fileId, Long userId) {
        return aiOrchestrationService.regenerateAi(fileId, userId);
    }

    @Override
    public List<LibraryFileVO> semanticSearch(String keyword, Long userId, int limit) {
        return aiOrchestrationService.semanticSearch(keyword, userId, limit);
    }

    @Override
    public int backfillEmbeddings() {
        return aiOrchestrationService.backfillEmbeddings();
    }

    @Override
    public void pollAiParseQueue() {
        aiOrchestrationService.pollAiParseQueue();
    }

    @Override
    public boolean transferToKnowledge(Long fileId, Long userId) {
        return aiOrchestrationService.transferToKnowledge(fileId, userId);
    }
}
