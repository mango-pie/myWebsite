package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
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
import com.ai.model.entity.User;
import com.ai.model.vo.library.LibraryCollectionVO;
import com.ai.model.vo.library.LibraryDownloadUrlVO;
import com.ai.model.vo.library.LibraryFileVO;
import com.ai.model.vo.library.LibraryRecycleFileVO;
import com.ai.model.vo.library.LibrarySummaryVO;
import com.ai.model.vo.library.LibraryTagVO;
import com.ai.model.vo.library.LibraryUploadInitVO;
import com.ai.service.UserService;
import com.ai.service.library.LibraryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 资料库（资料储藏室）：契约见 docs/p1-frontend-waiting-endpoints.md §4。
 */
@ConditionalOnModule("library")
@RestController
@RequestMapping("/library")
public class LibraryController {

    @Resource
    private LibraryService libraryService;

    @Resource
    private UserService userService;

    @GetMapping("/summary")
    public BaseResponse<LibrarySummaryVO> summary(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.summary(loginUser.getId()));
    }

    @GetMapping("/files")
    public BaseResponse<List<LibraryFileVO>> listFiles(LibraryFileQueryRequest queryRequest,
                                                       HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.listFiles(queryRequest, loginUser.getId()));
    }

    @GetMapping("/recycle")
    public BaseResponse<List<LibraryRecycleFileVO>> listRecycle(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.listRecycle(loginUser.getId()));
    }

    @GetMapping("/tags")
    public BaseResponse<List<LibraryTagVO>> listTags(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.listTags(loginUser.getId()));
    }

    @PostMapping("/tags")
    public BaseResponse<LibraryTagVO> createTag(@RequestBody LibraryTagCreateRequest createRequest,
                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.createTag(createRequest, loginUser.getId()));
    }

    @GetMapping("/collections")
    public BaseResponse<List<LibraryCollectionVO>> listCollections(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.listCollections(loginUser.getId()));
    }

    @PostMapping("/collections")
    public BaseResponse<LibraryCollectionVO> createCollection(@RequestBody LibraryCollectionCreateRequest createRequest,
                                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.createCollection(createRequest, loginUser.getId()));
    }

    @PutMapping("/collections/{id}")
    public BaseResponse<Boolean> updateCollection(@PathVariable Long id,
                                                  @RequestBody LibraryCollectionCreateRequest updateRequest,
                                                  HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.updateCollection(id, updateRequest, loginUser.getId()));
    }

    @DeleteMapping("/collections/{id}")
    public BaseResponse<Boolean> deleteCollection(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.deleteCollection(id, loginUser.getId()));
    }

    @PutMapping("/tags/{id}")
    public BaseResponse<LibraryTagVO> updateTag(@PathVariable Long id,
                                                @RequestBody LibraryTagCreateRequest updateRequest,
                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.updateTag(id, updateRequest, loginUser.getId()));
    }

    @DeleteMapping("/tags/{id}")
    public BaseResponse<Boolean> deleteTag(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.deleteTag(id, loginUser.getId()));
    }

    @PostMapping("/files/{id}/collections/remove")
    public BaseResponse<Boolean> removeFileCollection(@PathVariable Long id,
                                                      @RequestBody LibraryFileCollectionRemoveRequest removeRequest,
                                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.removeFileCollection(id, removeRequest, loginUser.getId()));
    }

    @PostMapping("/files/{id}/rename")
    public BaseResponse<LibraryFileVO> renameFile(@PathVariable Long id,
                                                  @RequestBody LibraryFileRenameRequest renameRequest,
                                                  HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.renameFile(id, renameRequest, loginUser.getId()));
    }

    @PostMapping("/files/upload-init")
    public BaseResponse<LibraryUploadInitVO> uploadInit(@RequestBody LibraryUploadInitRequest initRequest,
                                                        HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.uploadInit(initRequest, loginUser.getId()));
    }

    @PostMapping("/files/upload-complete")
    public BaseResponse<LibraryFileVO> uploadComplete(@RequestBody LibraryUploadCompleteRequest completeRequest,
                                                      HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.uploadComplete(completeRequest, loginUser.getId()));
    }

    /**
     * 浏览器无法直连内网 MinIO 时，把文件体经后端写入（与预签名 PUT 二选一）。
     */
    @PutMapping("/files/{id}/content")
    public BaseResponse<LibraryFileVO> uploadContent(@PathVariable Long id,
                                                     HttpServletRequest request) throws IOException {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.uploadContent(
                id, loginUser.getId(), request.getInputStream(), request.getContentLengthLong(),
                request.getContentType()));
    }

    /** 同域流式下载，带登录 Cookie，不依赖浏览器访问 MinIO。 */
    @GetMapping("/files/{id}/content")
    public void downloadContent(@PathVariable Long id, HttpServletRequest request,
                                HttpServletResponse response) {
        User loginUser = userService.getLoginUser(request);
        libraryService.streamDownload(id, loginUser.getId(), response);
    }

    @GetMapping("/files/{id}/download-url")
    public BaseResponse<LibraryDownloadUrlVO> downloadUrl(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.downloadUrl(id, loginUser.getId()));
    }

    @PostMapping("/files/{id}/open")
    public BaseResponse<Boolean> open(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.open(id, loginUser.getId()));
    }

    @PostMapping("/files/delete")
    public BaseResponse<Boolean> deleteFiles(@RequestBody LibraryIdsRequest idsRequest,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.deleteFiles(idsRequest, loginUser.getId()));
    }

    @PostMapping("/recycle/restore")
    public BaseResponse<Boolean> restore(@RequestBody LibraryIdsRequest idsRequest,
                                         HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.restore(idsRequest, loginUser.getId()));
    }

    @PostMapping("/recycle/purge")
    public BaseResponse<Boolean> purge(@RequestBody LibraryIdsRequest idsRequest,
                                       HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.purge(idsRequest, loginUser.getId()));
    }

    @PostMapping("/files/{id}/tags")
    public BaseResponse<Boolean> updateFileTags(@PathVariable Long id,
                                                @RequestBody LibraryFileTagsRequest tagsRequest,
                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.updateFileTags(id, tagsRequest, loginUser.getId()));
    }

    @PostMapping("/files/collections")
    public BaseResponse<Boolean> assignCollections(@RequestBody LibraryFilesCollectionsRequest collectionsRequest,
                                                   HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.assignCollections(collectionsRequest, loginUser.getId()));
    }

    @PostMapping("/files/{id}/transfer-knowledge")
    public BaseResponse<Boolean> transferToKnowledge(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.transferToKnowledge(id, loginUser.getId()));
    }

    @PostMapping("/files/{id}/ai-regenerate")
    public BaseResponse<Boolean> regenerateAi(@PathVariable Long id, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.regenerateAi(id, loginUser.getId()));
    }

    /** 语义搜索：向量相似度匹配文件名 + AI 摘要，返回带 score 的文件列表 */
    @GetMapping("/files/semantic-search")
    public BaseResponse<List<LibraryFileVO>> semanticSearch(@RequestParam String q,
                                                            @RequestParam(defaultValue = "20") int limit,
                                                            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(libraryService.semanticSearch(q, loginUser.getId(), limit));
    }
}
