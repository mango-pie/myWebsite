package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.User;
import com.ai.model.enums.UserRoleEnum;
import com.ai.service.BlogPostService;
import com.ai.service.BlogPostTagService;
import com.ai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@ConditionalOnModule("blog")
@RestController
@RequestMapping("/blog/postTag")
public class BlogPostTagController {

    @Autowired
    private BlogPostTagService blogPostTagService;

    @Autowired
    private BlogPostService blogPostService;

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    public BaseResponse<Boolean> addPostTag(@RequestParam Long postId, @RequestParam Long tagId,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(postId == null || tagId == null, ErrorCode.PARAMS_ERROR);
        assertCanManagePostTags(postId, request);
        boolean result = blogPostTagService.addPostTag(postId, tagId);
        return ResultUtils.success(result);
    }

    @PostMapping("/remove")
    public BaseResponse<Boolean> removePostTag(@RequestParam Long postId, @RequestParam Long tagId,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(postId == null || tagId == null, ErrorCode.PARAMS_ERROR);
        assertCanManagePostTags(postId, request);
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

    private void assertCanManagePostTags(Long postId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        BlogPost post = blogPostService.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        if (loginUser.getId() != null && loginUser.getId().equals(post.getUserId())) {
            return;
        }
        UserRoleEnum role = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        boolean staff = UserRoleEnum.ADMIN.equals(role) || UserRoleEnum.ADMINISTRATOR.equals(role);
        ThrowUtils.throwIf(!staff, ErrorCode.NO_AUTH_ERROR, "无权限修改此文章标签");
    }
}
