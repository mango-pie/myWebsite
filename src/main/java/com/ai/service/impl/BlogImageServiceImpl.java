package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.blog.BlogImageQueryRequest;
import com.ai.model.entity.BlogImage;
import com.ai.model.entity.User;
import com.ai.model.vo.blog.BlogImageVO;
import com.ai.service.BlogImageService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.ai.mapper.BlogImageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class BlogImageServiceImpl extends ServiceImpl<BlogImageMapper, BlogImage> implements BlogImageService {

    @Resource
    private UserService userService;

    @Override
    public long uploadImage(BlogImage blogImage) {
        if (blogImage == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        if (StrUtil.isBlank(blogImage.getFilename())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        if (StrUtil.isBlank(blogImage.getStorageName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存储名称不能为空");
        }
        if (StrUtil.isBlank(blogImage.getUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "访问URL不能为空");
        }
        if (blogImage.getSize() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能为空");
        }
        if (StrUtil.isBlank(blogImage.getType())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型不能为空");
        }

        blogImage.setCreatedTime(LocalDateTime.now());
        if (blogImage.getUsageType() == null) {
            blogImage.setUsageType(3);
        }
        if (blogImage.getStatus() == null) {
            blogImage.setStatus(1);
        }

        boolean saveResult = this.save(blogImage);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传图片失败");
        }

        return blogImage.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteImage(Long id, Long userId) {
        if (id == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogImage image = this.getById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }

        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除此图片");
        }

        image.setStatus(0);
        boolean updateResult = this.updateById(image);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除图片失败");
        }

        return true;
    }

    @Override
    public boolean updateImageStatus(Long id, Integer status, Long userId) {
        if (id == null || status == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogImage image = this.getById(id);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }

        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此图片");
        }

        image.setStatus(status);
        boolean updateResult = this.updateById(image);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新图片状态失败");
        }

        return true;
    }

    @Override
    public boolean bindImageToPost(Long imageId, Long postId, Long userId) {
        if (imageId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        BlogImage image = this.getById(imageId);
        if (image == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }

        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此图片");
        }

        image.setPostId(postId);
        boolean updateResult = this.updateById(image);
        if (!updateResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "绑定图片到文章失败");
        }

        return true;
    }

    @Override
    public BlogImageVO getImageVO(Long id) {
        if (id == null) {
            return null;
        }
        BlogImage image = this.getById(id);
        if (image == null) {
            return null;
        }
        return convertToVO(image);
    }

    @Override
    public Page<BlogImageVO> queryImagePage(BlogImageQueryRequest blogImageQueryRequest) {
        if (blogImageQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int pageNum = blogImageQueryRequest.getPageNum();
        int pageSize = blogImageQueryRequest.getPageSize();
        Page<BlogImage> imagePage = this.page(Page.of(pageNum, pageSize), getQueryWrapper(blogImageQueryRequest));

        Page<BlogImageVO> voPage = new Page<>(pageNum, pageSize, imagePage.getTotalRow());
        voPage.setRecords(imagePage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public QueryWrapper getQueryWrapper(BlogImageQueryRequest blogImageQueryRequest) {
        if (blogImageQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        Long id = blogImageQueryRequest.getId();
        Long postId = blogImageQueryRequest.getPostId();
        Long userId = blogImageQueryRequest.getUserId();
        Integer usageType = blogImageQueryRequest.getUsageType();
        Integer status = blogImageQueryRequest.getStatus();
        String searchText = blogImageQueryRequest.getSearchText();
        String sortField = blogImageQueryRequest.getSortField();
        String sortOrder = blogImageQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create();

        if (id != null) {
            queryWrapper.eq("id", id);
        }
        if (postId != null) {
            queryWrapper.eq("post_id", postId);
        }
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }
        if (usageType != null) {
            queryWrapper.eq("usage_type", usageType);
        }
        if (status != null) {
            queryWrapper.eq("status", status);
        }

        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and("(filename LIKE ? OR storage_name LIKE ?)", "%" + searchText + "%", "%" + searchText + "%");
        }

        queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        queryWrapper.orderBy("created_time", false);

        return queryWrapper;
    }

    @Override
    public List<BlogImage> getImagesByPostId(Long postId) {
        if (postId == null) {
            return Collections.emptyList();
        }

        return this.list(QueryWrapper.create()
                .where("post_id = ? and status = ?", postId, 1)
                .orderBy("created_time"));
    }

    private BlogImageVO convertToVO(BlogImage image) {
        if (image == null) {
            return null;
        }

        BlogImageVO vo = new BlogImageVO();
        BeanUtil.copyProperties(image, vo);

        vo.setStatusText(image.getStatus() == 1 ? "正常" : "删除");
        vo.setUsageTypeText(getUsageTypeText(image.getUsageType()));

        if (image.getUserId() != null) {
            User user = userService.getById(image.getUserId());
            if (user != null) {
                vo.setUserName(user.getUserName());
            }
        }

        return vo;
    }

    private String getUsageTypeText(Integer usageType) {
        if (usageType == null) {
            return "未知";
        }
        return switch (usageType) {
            case 1 -> "封面图片";
            case 2 -> "内容图片";
            case 3 -> "其他";
            default -> "未知";
        };
    }
}