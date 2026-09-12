package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ThrowUtils;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.blog.BlogCategoryAddRequest;
import com.ai.model.dto.blog.BlogCategoryQueryRequest;
import com.ai.model.dto.blog.BlogCategoryUpdateRequest;
import com.ai.model.entity.BlogCategory;
import com.ai.model.vo.blog.BlogCategoryVO;
import com.ai.service.BlogCategoryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog/category")
public class BlogCategoryController {

    @Autowired
    private BlogCategoryService blogCategoryService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addCategory(@RequestBody BlogCategoryAddRequest blogCategoryAddRequest) {
        ThrowUtils.throwIf(blogCategoryAddRequest == null, ErrorCode.PARAMS_ERROR);
        long result = blogCategoryService.addCategory(blogCategoryAddRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateCategory(@RequestBody BlogCategoryUpdateRequest blogCategoryUpdateRequest) {
        ThrowUtils.throwIf(blogCategoryUpdateRequest == null || blogCategoryUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogCategoryService.updateCategory(blogCategoryUpdateRequest);
        return ResultUtils.success(result);
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteCategory(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = blogCategoryService.deleteCategory(deleteRequest.getId());
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public BaseResponse<BlogCategoryVO> getCategoryVO(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        BlogCategoryVO vo = blogCategoryService.getCategoryVO(id);
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<BlogCategoryVO>> queryCategoryPage(@RequestBody BlogCategoryQueryRequest blogCategoryQueryRequest) {
        ThrowUtils.throwIf(blogCategoryQueryRequest == null, ErrorCode.PARAMS_ERROR);
        int pageNum = blogCategoryQueryRequest.getPageNum();
        int pageSize = blogCategoryQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR);
        Page<BlogCategoryVO> voPage = blogCategoryService.queryCategoryPage(blogCategoryQueryRequest);
        return ResultUtils.success(voPage);
    }

    @GetMapping("/list/all")
    public BaseResponse<List<BlogCategory>> getAllCategories() {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .where("status = ?", 1)
                .orderBy("sort_order");
        List<BlogCategory> categories = blogCategoryService.list(queryWrapper);
        return ResultUtils.success(categories);
    }
}