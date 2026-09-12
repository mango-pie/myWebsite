package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ThrowUtils;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.blog.BlogTagAddRequest;
import com.ai.model.dto.blog.BlogTagQueryRequest;
import com.ai.model.dto.blog.BlogTagUpdateRequest;
import com.ai.model.entity.BlogTag;
import com.ai.model.vo.blog.BlogTagVO;
import com.ai.service.BlogTagService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog/tag")
public class BlogTagController {

    @Autowired
    private BlogTagService blogTagService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addTag(@RequestBody BlogTagAddRequest blogTagAddRequest) {
        ThrowUtils.throwIf(blogTagAddRequest == null, ErrorCode.PARAMS_ERROR);
        long result = blogTagService.addTag(blogTagAddRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateTag(@RequestBody BlogTagUpdateRequest blogTagUpdateRequest) {
        ThrowUtils.throwIf(blogTagUpdateRequest == null || blogTagUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogTagService.updateTag(blogTagUpdateRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteTag(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogTagService.deleteTag(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public BaseResponse<BlogTagVO> getTagVO(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        BlogTagVO vo = blogTagService.getTagVO(id);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<BlogTagVO>> queryTagPage(@RequestBody BlogTagQueryRequest blogTagQueryRequest) {
        ThrowUtils.throwIf(blogTagQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = blogTagQueryRequest.getPageNum();
        int pageSize = blogTagQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR);
        Page<BlogTagVO> voPage = blogTagService.queryTagPage(blogTagQueryRequest);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/list/all")
    public BaseResponse<List<BlogTag>> getAllTags() {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("status = ?", 1)
                .orderBy("count");
        List<BlogTag> tags = blogTagService.list(queryWrapper);
        return ResultUtils.success(tags);
    }

    @GetMapping("/list/cloud")
    public BaseResponse<List<BlogTagVO>> getTagCloud() {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("status = ?", 1)
                .orderBy("count");
        List<BlogTag> tags = blogTagService.list(queryWrapper);
        List<BlogTagVO> voList = tags.stream()
                .map(tag -> {
                    BlogTagVO vo = new BlogTagVO();
                    org.springframework.beans.BeanUtils.copyProperties(tag, vo);
                    vo.setStatusText(tag.getStatus() == 1 ? "启用" : "禁用");
                    return vo;
                })
                .collect(java.util.stream.Collectors.toList());
        return ResultUtils.success(voList);
    }
}