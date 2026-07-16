package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.model.dto.knowledge.KnowledgeBaseCreateRequest;
import com.ai.model.dto.knowledge.KnowledgeBaseQueryRequest;
import com.ai.model.dto.knowledge.KnowledgeBaseUpdateRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.knowledge.KnowledgeBaseVO;
import com.ai.service.UserService;
import com.ai.service.knowledge.KnowledgeBaseService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/kb/knowledge-bases")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private UserService userService;

    @GetMapping
    public BaseResponse<Page<KnowledgeBaseVO>> page(KnowledgeBaseQueryRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeBaseService.page(request, loginUser.getId()));
    }

    @PostMapping
    public BaseResponse<KnowledgeBaseVO> create(@RequestBody KnowledgeBaseCreateRequest request,
                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeBaseService.create(request, loginUser.getId()));
    }

    @GetMapping("/{id}")
    public BaseResponse<KnowledgeBaseVO> get(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeBaseService.getVO(id, loginUser.getId()));
    }

    @PutMapping("/{id}")
    public BaseResponse<KnowledgeBaseVO> update(@PathVariable Long id,
                                                @RequestBody KnowledgeBaseUpdateRequest request,
                                                HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeBaseService.update(id, request, loginUser.getId()));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> delete(@PathVariable Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(knowledgeBaseService.delete(id, loginUser.getId()));
    }
}
