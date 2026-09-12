package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.BlogPostMapper;
import com.ai.model.dto.blog.BlogCategoryAddRequest;
import com.ai.model.dto.blog.BlogCategoryQueryRequest;
import com.ai.model.dto.blog.BlogCategoryUpdateRequest;
import com.ai.model.entity.BlogCategory;
import com.ai.model.entity.BlogPost;
import com.ai.model.vo.blog.BlogCategoryVO;
import com.ai.service.BlogCategoryService;
import com.ai.service.BlogPostService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ai.mapper.BlogCategoryMapper;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogCategoryServiceImpl extends ServiceImpl<BlogCategoryMapper, BlogCategory> implements BlogCategoryService {

    @Resource
        private BlogPostMapper blogPostMapper;

        @Resource
        private BlogPostService blogPostService;

    @Override
    public long addCategory(BlogCategoryAddRequest blogCategoryAddRequest) {
        if (blogCategoryAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogCategory existCategory = this.getOne(QueryWrapper.create()
                .where("name = ?", blogCategoryAddRequest.getName()));
        if (existCategory != null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类名称已存在");
        }

        BlogCategory category = new BlogCategory();
        BeanUtil.copyProperties(blogCategoryAddRequest, category);
        category.setCreatedTime(LocalDateTime.now());
        category.setUpdatedTime(LocalDateTime.now());

        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }

        boolean saveResult = this.save(category);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建分类失败");
        }

        return category.getId();
    }

    @Override
    public boolean updateCategory(BlogCategoryUpdateRequest blogCategoryUpdateRequest) {
        if (blogCategoryUpdateRequest == null || blogCategoryUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogCategory oldCategory = this.getById(blogCategoryUpdateRequest.getId());
        if (oldCategory == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分类不存在");
        }

        if (!oldCategory.getName().equals(blogCategoryUpdateRequest.getName())) {
            BlogCategory existCategory = this.getOne(QueryWrapper.create()
                    .where("name = ?", blogCategoryUpdateRequest.getName()));
            if (existCategory != null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "分类名称已存在");
            }
        }

        BlogCategory category = new BlogCategory();
        BeanUtil.copyProperties(blogCategoryUpdateRequest, category);
        category.setUpdatedTime(LocalDateTime.now());

        boolean updateResult = this.updateById(category);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新分类失败");
        }

        return true;
    }

    @Override
    public boolean deleteCategory(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogCategory category = this.getById(id);
        if (category == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分类不存在");
        }

        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("category_id", id)
                .isNull("deleted_time");
        long postCount = this.count(queryWrapper);
        if (postCount > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该分类下存在文章，无法删除");
        }

        boolean deleteResult = this.removeById(id);
        if (!deleteResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除分类失败");
        }

        return true;
    }

    @Override
    public BlogCategoryVO getCategoryVO(Long id) {
        if (id == null) {
            return null;
        }
        BlogCategory category = this.getById(id);
        if (category == null) {
            return null;
        }
        return convertToVO(category);
    }

    @Override
    public Page<BlogCategoryVO> queryCategoryPage(BlogCategoryQueryRequest blogCategoryQueryRequest) {
        if (blogCategoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int pageNum = blogCategoryQueryRequest.getPageNum();
        int pageSize = blogCategoryQueryRequest.getPageSize();
        Page<BlogCategory> categoryPage = this.page(Page.of(pageNum, pageSize), getQueryWrapper(blogCategoryQueryRequest));

        Page<BlogCategoryVO> voPage = new Page<>(pageNum, pageSize, categoryPage.getTotalRow());
        voPage.setRecords(categoryPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public QueryWrapper getQueryWrapper(BlogCategoryQueryRequest blogCategoryQueryRequest) {
        if (blogCategoryQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long id = blogCategoryQueryRequest.getId();
        String name = blogCategoryQueryRequest.getName();
        Integer status = blogCategoryQueryRequest.getStatus();
        String searchText = blogCategoryQueryRequest.getSearchText();
        String sortField = blogCategoryQueryRequest.getSortField();
        String sortOrder = blogCategoryQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create();

        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (StrUtil.isNotBlank(name)) {
            queryWrapper.like("name", name);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }

        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and("(name LIKE ? OR description LIKE ?)", "%" + searchText + "%", "%" + searchText + "%");
        }

        queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        queryWrapper.orderBy("sort_order", false);

        return queryWrapper;
    }

    private BlogCategoryVO convertToVO(BlogCategory category) {
        if (category == null) {
            return null;
        }

        BlogCategoryVO vo = new BlogCategoryVO();
        BeanUtil.copyProperties(category, vo);

        vo.setStatusText(category.getStatus() == 1 ? "启用" : "禁用");

        QueryWrapper queryWrapper = new QueryWrapper()
                .eq("category_id", category.getId())
                .isNull("deleted_time");
        // 使用原生 SQL 查询方式
        QueryWrapper postQueryWrapper = QueryWrapper.create()
                .where("category_id = ? AND deleted_time IS NULL", category.getId());
        long postCount = blogPostService.count(postQueryWrapper);
        vo.setPostCount((int) postCount);

        return vo;
    }
}