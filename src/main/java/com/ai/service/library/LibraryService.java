package com.ai.service.library;

import com.ai.model.dto.library.LibraryFileQueryRequest;
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

import java.util.List;

public interface LibraryService {

    LibrarySummaryVO summary(Long userId);

    List<LibraryFileVO> listFiles(LibraryFileQueryRequest request, Long userId);

    List<LibraryRecycleFileVO> listRecycle(Long userId);

    List<LibraryTagVO> listTags(Long userId);

    LibraryTagVO createTag(LibraryTagCreateRequest request, Long userId);

    List<LibraryCollectionVO> listCollections(Long userId);

    LibraryUploadInitVO uploadInit(LibraryUploadInitRequest request, Long userId);

    LibraryFileVO uploadComplete(LibraryUploadCompleteRequest request, Long userId);

    LibraryDownloadUrlVO downloadUrl(Long fileId, Long userId);

    boolean open(Long fileId, Long userId);

    boolean deleteFiles(LibraryIdsRequest request, Long userId);

    boolean restore(LibraryIdsRequest request, Long userId);

    boolean purge(LibraryIdsRequest request, Long userId);

    boolean updateFileTags(Long fileId, LibraryFileTagsRequest request, Long userId);

    boolean assignCollections(LibraryFilesCollectionsRequest request, Long userId);

    /** 转入知识库：切块 → 向量化 → 可 RAG 问答；异步执行，true 表示已入队 */
    boolean transferToKnowledge(Long fileId, Long userId);

    /** 重新生成 AI 摘要（异步任务，完成后 aiStatus=DONE） */
    boolean regenerateAi(Long fileId, Long userId);

    /** 语义搜索：按向量相似度返回带 score 的文件（不含回收站） */
    List<LibraryFileVO> semanticSearch(String keyword, Long userId, int limit);

    /** 定时任务：为已有摘要但缺向量的文件补齐 embedding，返回本轮补齐数 */
    int backfillEmbeddings();

    /** 定时任务：认领一条 PENDING 摘要任务移交工作线程 */
    void pollAiParseQueue();

    /** 定时任务：清理超过保留期的回收站文件（含 MinIO 对象） */
    int purgeExpiredRecycle();
}
