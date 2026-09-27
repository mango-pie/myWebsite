package com.ai.service.library;

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

import java.util.List;

public interface LibraryService {

    LibrarySummaryVO summary(Long userId);

    List<LibraryFileVO> listFiles(LibraryFileQueryRequest request, Long userId);

    List<LibraryRecycleFileVO> listRecycle(Long userId);

    List<LibraryTagVO> listTags(Long userId);

    LibraryTagVO createTag(LibraryTagCreateRequest request, Long userId);

    List<LibraryCollectionVO> listCollections(Long userId);

    LibraryCollectionVO createCollection(LibraryCollectionCreateRequest request, Long userId);

    /** 合集重命名（color 提供时一并更新），返回是否成功 */
    boolean updateCollection(Long collectionId, LibraryCollectionCreateRequest request, Long userId);

    /** 删除合集：文件保留，仅解除文件上的归属关联 */
    boolean deleteCollection(Long collectionId, Long userId);

    /** 标签重命名/换色（文件挂的是标签 id，无需同步文件行） */
    LibraryTagVO updateTag(Long tagId, LibraryTagCreateRequest request, Long userId);

    /** 删除标签：同步剥离所有文件上的该标签 */
    boolean deleteTag(Long tagId, Long userId);

    /** 文件移出合集（幂等：不在合集中视为已移除） */
    boolean removeFileCollection(Long fileId, LibraryFileCollectionRemoveRequest request, Long userId);

    /** 文件重命名（仅改 file_name，扩展名/对象键不变） */
    LibraryFileVO renameFile(Long fileId, LibraryFileRenameRequest request, Long userId);

    LibraryUploadInitVO uploadInit(LibraryUploadInitRequest request, Long userId);

    LibraryFileVO uploadComplete(LibraryUploadCompleteRequest request, Long userId);

    /**
     * 浏览器无法直连内网 MinIO 时，把文件体经后端写入对象存储并完成登记。
     */
    LibraryFileVO uploadContent(Long fileId, Long userId, java.io.InputStream body, long contentLength,
                                String contentType);

    /** 流式下载（同域，带登录态），避免把预签名里的 127.0.0.1 交给浏览器。 */
    void streamDownload(Long fileId, Long userId, jakarta.servlet.http.HttpServletResponse response);

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
