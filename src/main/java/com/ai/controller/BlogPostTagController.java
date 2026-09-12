package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ThrowUtils;
import com.ai.exception.ErrorCode;
import com.ai.model.entity.BlogPostTag;
import com.ai.model.entity.User;
import com.ai.service.BlogPostTagService;
import com.ai.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog/postTag")
public class BlogPostTagController {

    @Autowired
    private BlogPostTagService blogPostTagService;

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Boolean> addPostTag(@RequestParam Long postId, @RequestParam Long tagId) {
        ThrowUtils.throwIf(postId == null || tagId == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogPostTagService.addPostTag(postId, tagId);
        return ResultUtils.success(result);
    }

    @PostMapping("/remove")
    public BaseResponse<Boolean> removePostTag(@RequestParam Long postId, @RequestParam Long tagId) {
        ThrowUtils.throwIf(postId == null || tagId == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogPostTagService.removePostTag(postId, tagId);
        return ResultUtils.success(result);
    }

    @GetMapping("/list/tags/{postId}")
    public BaseResponse<List<Long>> getTagIdsByPostId(@PathVariable Long postId) {
        ThrowUtils.throwIf(postId == null, ErrorCode.PARAMS_ERROR);
        List<Long> tagIds = blogPostTagService.getTagIdsByPostId(postId);
        return ResultUtils.success(tagIds);
    }

    @GetMapping("/list/posts/{tagId}")
    public BaseResponse<List<Long>> getPostIdsByTagId(@PathVariable Long tagId) {
        ThrowUtils.throwIf(tagId == null, ErrorCode.PARAMS_ERROR);
        List<Long> postIds = blogPostTagService.getPostIdsByTagId(tagId);
        return ResultUtils.success(postIds);
    }

    @GetMapping("/check")
    public BaseResponse<Boolean> existsPostTag(@RequestParam Long postId, @RequestParam Long tagId) {
        ThrowUtils.throwIf(postId == null || tagId == null, ErrorCode.PARAMS_ERROR);
        boolean exists = blogPostTagService.existsPostTag(postId, tagId);
        return ResultUtils.success(exists);
    }
}
