package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.model.dto.knowledge.KnowledgeDocumentQueryRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.knowledge.KnowledgeChunkVO;
import com.ai.model.vo.knowledge.KnowledgeDocumentVO;
import com.ai.model.vo.knowledge.KnowledgeDownloadUrlVO;
import com.ai.service.UserService;
import com.ai.service.knowledge.KnowledgeDocumentService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@ConditionalOnModule("knowledge")
@RestController
@RequestMapping("/kb")
public class KnowledgeDocumentController {

    @Resource
    private KnowledgeDocumentService knowledgeDocumentService;

    @Resource
    private UserService userService;

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public BaseResponse<Page<KnowledgeDocumentVO>> page(@PathVariable Long kbId,
                                                        KnowledgeDocumentQueryRequest request,
                                                        HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.pageByKnowledgeBase(kbId, request, loginUser.getId()));
    }

    @PostMapping(value = "/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<KnowledgeDocumentVO> upload(@PathVariable Long kbId,
                                                    @RequestPart("file") MultipartFile file,
                                                    HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.upload(kbId, file, loginUser.getId()));
    }

    @GetMapping("/documents/{id}")
    public BaseResponse<KnowledgeDocumentVO> get(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.getVO(id, loginUser.getId()));
    }

    @DeleteMapping("/documents/{id}")
    public BaseResponse<Boolean> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.delete(id, loginUser.getId()));
    }

    @GetMapping("/documents/{id}/download-url")
    public BaseResponse<KnowledgeDownloadUrlVO> downloadUrl(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.getDownloadUrl(id, loginUser.getId()));
    }

    @PostMapping("/documents/{id}/parse")
    public BaseResponse<KnowledgeDocumentVO> parse(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.parse(id, loginUser.getId()));
    }

    @GetMapping("/documents/{id}/chunks")
    public BaseResponse<List<KnowledgeChunkVO>> chunks(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "1") int pageNum,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeDocumentService.listChunks(id, pageNum, pageSize, loginUser.getId()));
    }
}
