package com.ai.controller;

import cn.hutool.core.bean.BeanUtil;
import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.blog.BlogPostAddRequest;
import com.ai.model.dto.blog.BlogPostQueryRequest;
import com.ai.model.dto.blog.BlogPostUpdateRequest;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.User;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.service.BlogPostService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/blog/post")
public class BlogPostController {

    @Autowired
    private BlogPostService blogPostService;

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Long> addBlogPost(@RequestBody BlogPostAddRequest blogPostAddRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(blogPostAddRequest == null, ErrorCode.PARAMS_ERROR);
        long result = blogPostService.addBlogPost(blogPostAddRequest, loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateBlogPost(@RequestBody BlogPostUpdateRequest blogPostUpdateRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(blogPostUpdateRequest == null || blogPostUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogPostService.updateBlogPost(blogPostUpdateRequest, loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteBlogPost(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogPostService.deleteBlogPost(deleteRequest.getId(), loginUser.getId());
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public BaseResponse<BlogPostVO> getBlogPostVO(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        BlogPostVO vo = blogPostService.getBlogPostVO(id);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<BlogPostVO>> queryBlogPostPage(@RequestBody BlogPostQueryRequest blogPostQueryRequest) {
        ThrowUtils.throwIf(blogPostQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = blogPostQueryRequest.getPageNum();
        int pageSize = blogPostQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR);
        Page<BlogPostVO> voPage = blogPostService.queryBlogPostPage(blogPostQueryRequest);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/list/page/category/{categoryId}")
    public BaseResponse<Page<BlogPostVO>> getBlogPostPageByCategory(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        ThrowUtils.throwIf(categoryId == null || categoryId <= 0, ErrorCode.PARAMS_ERROR);
        Page<BlogPostVO> voPage = blogPostService.getBlogPostPageByCategory(categoryId, pageNum, pageSize);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/list/page/tag/{tagId}")
    public BaseResponse<Page<BlogPostVO>> getBlogPostPageByTag(
            @PathVariable Long tagId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        ThrowUtils.throwIf(tagId == null || tagId <= 0, ErrorCode.PARAMS_ERROR);
        Page<BlogPostVO> voPage = blogPostService.getBlogPostPageByTag(tagId, pageNum, pageSize);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/list/page/published")
    public BaseResponse<Page<BlogPostVO>> getPublishedBlogPostPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<BlogPostVO> voPage = blogPostService.getPublishedBlogPostPage(pageNum, pageSize);
        return ResultUtils.success(voPage);
    }

    @PostMapping("/update/status")
    public BaseResponse<Boolean> updateBlogPostStatus(@RequestParam Long id, @RequestParam Integer status, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = blogPostService.updateBlogPostStatus(id, status, loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/update/top")
    public BaseResponse<Boolean> toggleTopStatus(@RequestParam Long id, @RequestParam Integer isTop, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = blogPostService.toggleTopStatus(id, isTop, loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/view/{id}")
    public BaseResponse<Boolean> incrementViewCount(@PathVariable Long id) {
        boolean result = blogPostService.incrementViewCount(id);
        return ResultUtils.success(result);
    }

    @PostMapping("/like/{id}")
    public BaseResponse<Boolean> incrementLikeCount(@PathVariable Long id) {
        boolean result = blogPostService.incrementLikeCount(id);
        return ResultUtils.success(result);
    }
}
