# 博客模块技术实现文档

## 1. 概述

本文档详细说明博客模块的技术实现，包括代码结构设计、业务逻辑实现、API接口规范以及与其他模块的集成方式。博客模块基于 `blog_database_design.md` 中定义的数据库设计实现，遵循项目的代码规范和设计模式。

### 1.1 模块组成

博客模块由以下五个核心子模块组成：

| 子模块 | 功能描述 | 核心实体 |
|--------|----------|----------|
| 博客文章（BlogPost） | 文章的创建、编辑、删除、查询 | BlogPost |
| 博客分类（BlogCategory） | 文章分类的管理 | BlogCategory |
| 博客标签（BlogTag） | 文章标签的管理 | BlogTag |
| 文章标签关系（BlogPostTag） | 文章与标签的关联关系 | BlogPostTag |
| 博客图片（BlogImage） | 文章图片资源管理 | BlogImage |

### 1.2 技术栈

- **框架**：Spring Boot 3.x
- **ORM框架**：MyBatis-Flex
- **数据校验**：Hutool工具库 + Spring Validation
- **响应格式**：统一 BaseResponse 包装

## 2. 代码结构

### 2.1 目录结构

```
src/main/java/com/ai/
├── controller/
│   ├── BlogPostController.java          # 文章接口
│   ├── BlogCategoryController.java     # 分类接口
│   ├── BlogTagController.java          # 标签接口
│   ├── BlogImageController.java        # 图片接口
│   └── BlogPostTagController.java      # 标签关系接口
├── service/
│   ├── BlogPostService.java            # 文章服务接口
│   ├── BlogCategoryService.java        # 分类服务接口
│   ├── BlogTagService.java             # 标签服务接口
│   ├── BlogImageService.java           # 图片服务接口
│   ├── BlogPostTagService.java         # 标签关系服务接口
│   └── impl/
│       ├── BlogPostServiceImpl.java
│       ├── BlogCategoryServiceImpl.java
│       ├── BlogTagServiceImpl.java
│       ├── BlogImageServiceImpl.java
│       └── BlogPostTagServiceImpl.java
├── mapper/
│   ├── BlogPostMapper.java
│   ├── BlogCategoryMapper.java
│   ├── BlogTagMapper.java
│   ├── BlogImageMapper.java
│   └── BlogPostTagMapper.java
└── model/
    ├── dto/blog/
    │   ├── BlogPostQueryRequest.java
    │   ├── BlogPostAddRequest.java
    │   ├── BlogPostUpdateRequest.java
    │   ├── BlogCategoryQueryRequest.java
    │   ├── BlogCategoryAddRequest.java
    │   ├── BlogCategoryUpdateRequest.java
    │   ├── BlogTagQueryRequest.java
    │   ├── BlogTagAddRequest.java
    │   ├── BlogTagUpdateRequest.java
    │   └── BlogImageQueryRequest.java
    ├── entity/
    │   ├── BlogPost.java
    │   ├── BlogCategory.java
    │   ├── BlogTag.java
    │   ├── BlogImage.java
    │   └── BlogPostTag.java
    └── vo/blog/
        ├── BlogPostVO.java
        ├── BlogCategoryVO.java
        ├── BlogTagVO.java
        └── BlogImageVO.java
```

### 2.2 请求与响应模型

#### 2.2.1 请求模型设计

所有查询请求均继承 `PageRequest` 基类，包含分页和排序参数：

```java
public class PageRequest {
    private int pageNum = 1;       // 当前页号
    private int pageSize = 10;     // 页面大小
    private String sortField;      // 排序字段
    private String sortOrder = "descend";  // 排序顺序
}
```

添加和更新请求模型包含完整的业务字段，并添加必要的数据校验注解。

#### 2.2.2 视图对象设计

VO对象用于向前端返回脱敏和转换后的数据，包含状态文本转换和关联数据聚合。

## 3. 核心业务实现

### 3.1 博客文章（BlogPost）

#### 3.1.1 核心业务逻辑

**文章创建**

```java
@Transactional(rollbackFor = Exception.class)
public long addBlogPost(BlogPostAddRequest blogPostAddRequest, Long userId) {
    // 1. 参数校验
    // 2. 构建文章实体，设置初始值（浏览量0、点赞数0、创建时间等）
    // 3. 保存文章
    // 4. 如果有标签，保存文章-标签关联关系
    return blogPost.getId();
}
```

**文章更新**

```java
@Transactional(rollbackFor = Exception.class)
public boolean updateBlogPost(BlogPostUpdateRequest blogPostUpdateRequest, Long userId) {
    // 1. 参数校验
    // 2. 校验文章存在性和权限
    // 3. 更新文章基础信息
    // 4. 重建文章-标签关联关系（先删除旧关系，再添加新关系）
    return true;
}
```

**文章删除（软删除）**

```java
@Transactional(rollbackFor = Exception.class)
public boolean deleteBlogPost(Long id, Long userId) {
    // 1. 参数校验
    // 2. 校验文章存在性和权限
    // 3. 设置 deletedTime 实现软删除
    // 4. 删除关联的标签关系
    return true;
}
```

**浏览量与点赞数增长**

使用数据库原子操作保证并发安全：

```java
@Update("UPDATE blog_post SET view_count = view_count + 1 WHERE id = #{id}")
int incrementViewCount(@Param("id") Long id);

@Update("UPDATE blog_post SET like_count = like_count + 1 WHERE id = #{id}")
int incrementLikeCount(@Param("id") Long id);
```

#### 3.1.2 查询功能

**动态查询构建**

支持多条件组合查询和全文搜索：

```java
public QueryWrapper getQueryWrapper(BlogPostQueryRequest request) {
    return QueryWrapper.create()
        .eq(id != null, "id", id)
        .like(StrUtil.isNotBlank(title), "title", title)
        .like(StrUtil.isNotBlank(summary), "summary", summary)
        .eq(categoryId != null, "category_id", categoryId)
        .eq(status != null, "status", status)
        .isNull("deleted_time")  // 只查询未删除的记录
        .and(searchText != null, wrapper -> wrapper
            .like("title", searchText)
            .or()
            .like("summary", searchText));
}
```

**按分类和标签查询**

```java
public Page<BlogPostVO> getBlogPostPageByCategory(Long categoryId, int pageNum, int pageSize) {
    return this.page(Page.of(pageNum, pageSize),
        QueryWrapper.create()
            .eq("category_id", categoryId)
            .eq("status", 1)  // 只查询已发布的文章
            .orderBy("is_top", false)
            .orderBy("created_time", false));
}

public Page<BlogPostVO> getBlogPostPageByTag(Long tagId, int pageNum, int pageSize) {
    List<Long> postIds = blogPostTagService.getPostIdsByTagId(tagId);
    return this.page(Page.of(pageNum, pageSize),
        QueryWrapper.create()
            .in("id", postIds)
            .eq("status", 1));
}
```

#### 3.1.3 权限控制

所有写操作都需要验证用户身份和权限：

- 创建文章：验证登录用户
- 更新/删除文章：验证文章作者或管理员
- 状态变更：验证文章作者

### 3.2 博客分类（BlogCategory）

#### 3.2.1 核心业务逻辑

**分类创建**

```java
public long addCategory(BlogCategoryAddRequest request) {
    // 1. 检查分类名称是否已存在
    // 2. 设置默认排序值和状态
    // 3. 保存分类
    return category.getId();
}
```

**分类删除**

```java
public boolean deleteCategory(Long id) {
    // 1. 检查分类是否存在
    // 2. 检查该分类下是否有文章
    // 3. 如果有文章，禁止删除
    return this.removeById(id);
}
```

#### 3.2.2 VO转换

```java
private BlogCategoryVO convertToVO(BlogCategory category) {
    BlogCategoryVO vo = new BlogCategoryVO();
    BeanUtil.copyProperties(category, vo);
    vo.setStatusText(category.getStatus() == 1 ? "启用" : "禁用");
    
    // 动态计算该分类下的文章数量
    long postCount = blogPostService.count(
        QueryWrapper.create()
            .eq("category_id", category.getId())
            .isNull("deleted_time"));
    vo.setPostCount((int) postCount);
    
    return vo;
}
```

### 3.3 博客标签（BlogTag）

#### 3.3.1 核心业务逻辑

**标签使用计数管理**

```java
public boolean incrementCount(Long tagId) {
    return this.mapper.incrementCount(tagId) > 0;
}

public boolean decrementCount(Long tagId) {
    return this.mapper.decrementCount(tagId) > 0;
}
```

**获取文章的所有标签**

```java
public List<BlogTag> getTagsByPostId(Long postId) {
    List<BlogPostTag> postTags = blogPostTagService.query()
        .where("post_id").eq(postId).list();
    
    if (CollUtil.isEmpty(postTags)) {
        return Collections.emptyList();
    }
    
    List<Long> tagIds = postTags.stream()
        .map(BlogPostTag::getTagId)
        .collect(Collectors.toList());
    
    return this.query()
        .where("id").in(tagIds)
        .and()
        .where("status").eq(1)  // 只返回启用状态的标签
        .list();
}
```

### 3.4 文章标签关系（BlogPostTag）

#### 3.4.1 关系管理

```java
public boolean addPostTag(Long postId, Long tagId) {
    // 防止重复关联
    if (existsPostTag(postId, tagId)) {
        return true;
    }
    
    BlogPostTag postTag = BlogPostTag.builder()
        .postId(postId)
        .tagId(tagId)
        .createdTime(LocalDateTime.now())
        .build();
    
    return this.save(postTag);
}

public List<Long> getPostIdsByTagId(Long tagId) {
    return this.query()
        .where("tag_id").eq(tagId)
        .list()
        .stream()
        .map(BlogPostTag::getPostId)
        .collect(Collectors.toList());
}
```

### 3.5 博客图片（BlogImage）

#### 3.5.1 图片管理

**上传图片记录**

```java
public long uploadImage(BlogImage blogImage) {
    // 完整参数校验
    // 设置默认使用类型和状态
    // 保存图片信息
    return blogImage.getId();
}
```

**软删除图片**

```java
public boolean deleteImage(Long id, Long userId) {
    // 校验图片存在性和所有权
    // 使用状态字段实现软删除
    image.setStatus(0);
    return this.updateById(image);
}
```

**绑定图片到文章**

```java
public boolean bindImageToPost(Long imageId, Long postId, Long userId) {
    // 校验权限
    // 更新关联的文章ID
    image.setPostId(postId);
    return this.updateById(image);
}
```

## 4. API接口规范

### 4.1 统一响应格式

所有接口统一使用 `BaseResponse<T>` 包装返回值：

```json
{
    "code": 0,
    "data": {},
    "message": "ok"
}
```

### 4.2 文章接口（/blog/post）

| 接口路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/blog/post/add` | POST | 创建文章 | 登录用户 |
| `/blog/post/update` | POST | 更新文章 | 文章作者 |
| `/blog/post/delete` | POST | 删除文章 | 文章作者 |
| `/blog/post/get/vo` | GET | 获取文章详情 | 公开 |
| `/blog/post/list/page/vo` | POST | 分页查询文章 | 公开 |
| `/blog/post/list/page/category/{categoryId}` | GET | 按分类查询文章 | 公开 |
| `/blog/post/list/page/tag/{tagId}` | GET | 按标签查询文章 | 公开 |
| `/blog/post/list/page/published` | GET | 获取已发布文章列表 | 公开 |
| `/blog/post/update/status` | POST | 更新文章状态 | 文章作者 |
| `/blog/post/update/top` | POST | 设置置顶状态 | 文章作者 |
| `/blog/post/view/{id}` | POST | 增加浏览量 | 公开 |
| `/blog/post/like/{id}` | POST | 增加点赞数 | 公开 |

### 4.3 分类接口（/blog/category）

| 接口路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/blog/category/add` | POST | 创建分类 | 管理员 |
| `/blog/category/update` | POST | 更新分类 | 管理员 |
| `/blog/category/delete` | POST | 删除分类 | 管理员 |
| `/blog/category/get/vo` | GET | 获取分类详情 | 公开 |
| `/blog/category/list/page/vo` | POST | 分页查询分类 | 公开 |
| `/blog/category/list/all` | GET | 获取所有启用分类 | 公开 |

### 4.4 标签接口（/blog/tag）

| 接口路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/blog/tag/add` | POST | 创建标签 | 管理员 |
| `/blog/tag/update` | POST | 更新标签 | 管理员 |
| `/blog/tag/delete` | POST | 删除标签 | 管理员 |
| `/blog/tag/get/vo` | GET | 获取标签详情 | 公开 |
| `/blog/tag/list/page/vo` | POST | 分页查询标签 | 公开 |
| `/blog/tag/list/all` | GET | 获取所有启用标签 | 公开 |
| `/blog/tag/list/cloud` | GET | 获取标签云 | 公开 |

### 4.5 图片接口（/blog/image）

| 接口路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/blog/image/upload` | POST | 上传图片 | 登录用户 |
| `/blog/image/delete` | POST | 删除图片 | 图片所有者 |
| `/blog/image/update/status` | POST | 更新图片状态 | 图片所有者 |
| `/blog/image/bind/post` | POST | 绑定图片到文章 | 图片所有者 |
| `/blog/image/get/vo` | GET | 获取图片详情 | 公开 |
| `/blog/image/list/page/vo` | POST | 分页查询图片 | 管理员 |
| `/blog/image/list/by/post/{postId}` | GET | 获取文章的所有图片 | 公开 |

### 4.6 标签关系接口（/blog/postTag）

| 接口路径 | 方法 | 功能 | 权限 |
|---------|------|------|------|
| `/blog/postTag/add` | POST | 添加文章标签关联 | 登录用户 |
| `/blog/postTag/remove` | POST | 移除文章标签关联 | 登录用户 |
| `/blog/postTag/list/tags/{postId}` | GET | 获取文章的标签列表 | 公开 |
| `/blog/postTag/list/posts/{tagId}` | GET | 获取标签下的文章列表 | 公开 |
| `/blog/postTag/check` | GET | 检查文章是否关联某标签 | 公开 |

## 5. 数据转换与聚合

### 5.1 VO转换机制

在 Service 层实现实体到 VO 的转换，转换过程中完成以下处理：

1. **属性拷贝**：使用 Hutool 的 BeanUtil.copyProperties
2. **状态文本转换**：将数字状态码转换为可读文本
3. **关联数据聚合**：查询并填充关联实体的信息

```java
private BlogPostVO convertToVO(BlogPost blogPost) {
    BlogPostVO vo = new BlogPostVO();
    BeanUtil.copyProperties(blogPost, vo);
    
    // 状态文本转换
    vo.setStatusText(getStatusText(blogPost.getStatus()));
    
    // 填充分类信息
    if (blogPost.getCategoryId() != null) {
        BlogCategory category = blogCategoryService.getById(blogPost.getCategoryId());
        vo.setCategoryName(category != null ? category.getName() : null);
    }
    
    // 填充用户信息
    if (blogPost.getUserId() != null) {
        User user = userService.getById(blogPost.getUserId());
        vo.setUserName(user != null ? user.getUserName() : null);
        vo.setUserAvatar(user != null ? user.getAvatar() : null);
    }
    
    // 填充标签列表
    List<BlogTag> tags = blogTagService.getTagsByPostId(blogPost.getId());
    vo.setTags(tags.stream().map(this::convertTagToVO).collect(Collectors.toList()));
    
    return vo;
}
```

### 5.2 分页转换

分页查询时，需要将实体分页结果转换为 VO 分页结果：

```java
public Page<BlogPostVO> queryBlogPostPage(BlogPostQueryRequest request) {
    int pageNum = request.getPageNum();
    int pageSize = request.getPageSize();
    
    // 查询实体分页
    Page<BlogPost> postPage = this.page(
        Page.of(pageNum, pageSize), 
        getQueryWrapper(request)
    );
    
    // 转换为VO分页
    Page<BlogPostVO> voPage = new Page<>(pageNum, pageSize, postPage.getTotalRow());
    voPage.setRecords(
        postPage.getRecords()
            .stream()
            .map(this::convertToVO)
            .collect(Collectors.toList())
    );
    
    return voPage;
}
```

## 6. 事务管理

### 6.1 事务边界

以下操作需要添加 `@Transactional` 注解保证数据一致性：

- **文章创建**：同时保存文章和标签关联关系
- **文章更新**：更新文章信息和重建标签关联关系
- **文章删除**：软删除文章和删除标签关联关系
- **标签删除**：删除标签和删除所有相关联的标签关系
- **标签关联**：添加/删除标签关系

### 6.2 事务配置

```java
@Transactional(rollbackFor = Exception.class)
public long addBlogPost(BlogPostAddRequest request, Long userId) {
    // 业务逻辑
}
```

## 7. 异常处理

### 7.1 业务异常

使用统一的 `BusinessException` 处理业务逻辑错误：

```java
if (blogPost == null) {
    throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "文章不存在");
}

if (!blogPost.getUserId().equals(userId)) {
    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限修改此文章");
}
```

### 7.2 参数校验

在 Controller 层使用 `ThrowUtils.throwIf` 进行参数校验：

```java
@PostMapping("/update")
public BaseResponse<Boolean> updateBlogPost(
    @RequestBody BlogPostUpdateRequest request, 
    HttpServletRequest httpRequest
) {
    User loginUser = userService.getLoginUser(httpRequest);
    ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
    ThrowUtils.throwIf(request.getId() == null, ErrorCode.PARAMS_ERROR);
    boolean result = blogPostService.updateBlogPost(request, loginUser.getId());
    return ResultUtils.success(result);
}
```

## 8. 性能优化

### 8.1 索引策略

数据库层面建立了以下索引优化查询性能：

- `idx_category_id`：按分类查询文章
- `idx_user_id`：按作者查询文章
- `idx_status`：按状态筛选
- `idx_is_top`：置顶文章筛选
- `idx_created_time`：按时间排序
- `uk_post_tag (post_id, tag_id)`：文章标签关系唯一约束

### 8.2 计数操作优化

浏览量和点赞数使用原子操作 `UPDATE ... SET count = count + 1`，避免读-改-写操作：

```java
@Update("UPDATE blog_post SET view_count = view_count + 1 WHERE id = #{id}")
int incrementViewCount(@Param("id") Long id);
```

### 8.3 查询优化

- 使用 `CollUtil.isEmpty()` 提前判断空集合，避免不必要的查询
- 关联查询时先查询 ID 列表，再批量查询详情
- 使用分页限制返回数据量

## 9. 扩展性设计

### 9.1 预留扩展字段

`BlogPost` 实体包含 `extend_info` JSON 字段，用于存储未来可能扩展的非核心数据，如：

- SEO信息
- 阅读时间预估
- 社交分享数据
- 自定义元数据

### 9.2 状态码扩展

使用 TINYINT 类型存储状态，预留扩展空间：

| 字段 | 当前使用值 | 预留扩展 |
|------|-----------|---------|
| blog_post.status | 0-草稿, 1-发布, 2-下架 | 3-审核中, 4-已归档 |
| blog_image.usage_type | 1-封面, 2-内容图片, 3-其他 | 4-头像, 5-广告图 |

### 9.3 未来功能扩展

根据 `blog_database_design.md` 的规划，未来可扩展的功能包括：

| 功能 | 建议表结构 | 说明 |
|------|-----------|------|
| 评论功能 | blog_comment | 文章评论和回复 |
| 点赞记录 | blog_like | 详细记录用户点赞行为 |
| 文章系列 | blog_series | 将多篇文章组织成系列 |
| 统计数据 | blog_statistics | 阅读统计和用户行为分析 |

## 10. 使用示例

### 10.1 创建文章

```json
POST /blog/post/add
Content-Type: application/json
Cookie: SESSION=xxx

{
    "title": "Spring Boot 最佳实践",
    "summary": "本文介绍Spring Boot开发的核心最佳实践...",
    "content": "# Spring Boot 最佳实践\n\n## 前言\n...",
    "coverUrl": "https://example.com/cover.jpg",
    "categoryId": 1,
    "tagIds": [1, 2, 3],
    "status": 1,
    "isTop": 0
}
```

响应：

```json
{
    "code": 0,
    "data": 123,
    "message": "ok"
}
```

### 10.2 查询已发布文章

```json
POST /blog/post/list/page/published
Content-Type: application/json

{
    "pageNum": 1,
    "pageSize": 10
}
```

响应：

```json
{
    "code": 0,
    "data": {
        "pageNumber": 1,
        "pageSize": 10,
        "totalRow": 100,
        "records": [
            {
                "id": 123,
                "title": "Spring Boot 最佳实践",
                "summary": "本文介绍...",
                "coverUrl": "https://...",
                "categoryName": "后端开发",
                "userName": "张三",
                "userAvatar": "https://...",
                "viewCount": 1000,
                "likeCount": 50,
                "statusText": "发布",
                "isTop": 0,
                "tags": [
                    {"id": 1, "name": "Java", "color": "#667eea"},
                    {"id": 2, "name": "Spring", "color": "#764ba2"}
                ],
                "createdTime": "2024-01-15 10:30:00"
            }
        ]
    },
    "message": "ok"
}
```

### 10.3 按分类查询文章

```
GET /blog/post/list/page/category/1?pageNum=1&pageSize=10
```

### 10.4 增加浏览量

```
POST /blog/post/view/123
```

## 11. 与其他模块的集成

### 11.1 用户模块集成

文章和图片模块需要关联用户信息：

```java
@Resource
private UserService userService;

// 获取文章作者信息
User user = userService.getById(blogPost.getUserId());
vo.setUserName(user.getUserName());
vo.setUserAvatar(user.getAvatar());
```

### 11.2 模块间依赖关系

```
BlogPostService
    ├── BlogCategoryService (获取分类信息)
    ├── BlogTagService (获取标签信息、计数)
    ├── BlogPostTagService (管理标签关联)
    └── UserService (获取用户信息)

BlogCategoryService
    └── BlogPostService (检查分类下的文章数)

BlogTagService
    └── BlogPostTagService (管理关联关系)

BlogImageService
    └── UserService (获取上传用户信息)
```

## 12. 总结

博客模块的实现遵循了以下设计原则：

1. **分层清晰**：Controller-Service-Mapper 三层架构，职责明确
2. **统一规范**：统一的响应格式、异常处理、参数校验
3. **事务安全**：关键操作使用事务保证数据一致性
4. **性能优先**：合理的索引设计、原子操作、查询优化
5. **可扩展性**：预留扩展字段和状态码，便于功能扩展
6. **安全控制**：基于用户身份和所有权的权限校验

模块完整实现了博客系统的核心功能，为前端页面提供了完整的后端接口支持。
