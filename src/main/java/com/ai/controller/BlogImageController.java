package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ThrowUtils;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.blog.BlogImageQueryRequest;
import com.ai.model.entity.BlogImage;
import com.ai.model.entity.User;
import com.ai.model.vo.blog.BlogImageVO;
import com.ai.service.BlogImageService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog/image")
public class BlogImageController {

    @Autowired
    private BlogImageService blogImageService;

    @Autowired
    private UserService userService;

    @PostMapping("/upload")
    public BaseResponse<Long> uploadImage(@RequestBody BlogImage blogImage, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(blogImage == null, ErrorCode.PARAMS_ERROR);
        blogImage.setUserId(loginUser.getId());
        long result = blogImageService.uploadImage(blogImage);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteImage(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogImageService.deleteImage(deleteRequest.getId(), loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/update/status")
    public BaseResponse<Boolean> updateImageStatus(@RequestParam Long id, @RequestParam Integer status, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = blogImageService.updateImageStatus(id, status, loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/bind/post")
    public BaseResponse<Boolean> bindImageToPost(@RequestParam Long imageId, @RequestParam(required = false) Long postId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean result = blogImageService.bindImageToPost(imageId, postId, loginUser.getId());
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public BaseResponse<BlogImageVO> getImageVO(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        BlogImageVO vo = blogImageService.getImageVO(id);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<BlogImageVO>> queryImagePage(@RequestBody BlogImageQueryRequest blogImageQueryRequest) {
        ThrowUtils.throwIf(blogImageQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = blogImageQueryRequest.getPageNum();
        int pageSize = blogImageQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR);
        Page<BlogImageVO> voPage = blogImageService.queryImagePage(blogImageQueryRequest);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/list/by/post/{postId}")
    public BaseResponse<List<BlogImage>> getImagesByPostId(@PathVariable Long postId) {
        List<BlogImage> images = blogImageService.getImagesByPostId(postId);
        return ResultUtils.success(images);
    }
}
