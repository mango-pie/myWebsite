# 02 V1 后端开发方案

> Ai-Backend 新功能第一版后端。配套：[01-v1-mvp.md](./01-v1-mvp.md)、[03-v1-frontend.md](./03-v1-frontend.md)。

## 1. 后端目标

后端需要把现有知识库能力扩展为“精读工作台”：

```text
采集原文 → AI 精炼 → 预览编辑 → 发布博客 → 加入知识库 → 同步 / 重建
```

第一版仍然同步执行，不引入异步任务队列。所有新增管理接口统一放在：

```text
/api/admin/knowledge/**
```

## 2. 数据库变更

### 2.1 `knowledge_note` 增量字段

在现有表基础上增加：

```sql
ALTER TABLE knowledge_note
    ADD COLUMN knowledge_document_id BIGINT DEFAULT NULL COMMENT '精读稿入库后关联的知识库文档',
    ADD COLUMN publish_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PUBLISHED' COMMENT 'NOT_PUBLISHED/DRAFT_CREATED/PUBLISHED/SYNC_REQUIRED/SYNC_FAILED',
    ADD COLUMN index_status VARCHAR(32) NOT NULL DEFAULT 'NOT_INDEXED' COMMENT 'NOT_INDEXED/INDEXED/REINDEX_REQUIRED/INDEX_FAILED',
    ADD COLUMN last_distilled_at DATETIME DEFAULT NULL COMMENT '最近 AI 蒸馏时间',
    ADD COLUMN last_edited_at DATETIME DEFAULT NULL COMMENT '最近人工编辑时间',
    ADD COLUMN last_published_at DATETIME DEFAULT NULL COMMENT '最近同步博客时间',
    ADD COLUMN last_indexed_at DATETIME DEFAULT NULL COMMENT '最近向量入库时间',
    ADD KEY idx_knote_document (knowledge_document_id),
    ADD KEY idx_knote_publish_status (publish_status),
    ADD KEY idx_knote_index_status (index_status);
```

如果已有初始化 SQL，则同步更新 `src/main/resources/sql/knowledge_schema.sql` 中的建表语句。

### 2.2 状态定义

`knowledge_note.status` 只表示精读主状态：

```text
SUCCESS
FAILED
DELETED
```

博客发布状态：

```text
NOT_PUBLISHED
DRAFT_CREATED
PUBLISHED
SYNC_REQUIRED
SYNC_FAILED
```

知识库索引状态：

```text
NOT_INDEXED
INDEXED
REINDEX_REQUIRED
INDEX_FAILED
```

## 3. 实体与 DTO

### 3.1 `KnowledgeNote`

新增字段：

```java
private Long knowledgeDocumentId;
private String publishStatus;
private String indexStatus;
private LocalDateTime lastDistilledAt;
private LocalDateTime lastEditedAt;
private LocalDateTime lastPublishedAt;
private LocalDateTime lastIndexedAt;
```

### 3.2 新增请求对象

`KnowledgeNoteQueryRequest`

```java
private String keyword;
private String sourceType;
private String publishStatus;
private String indexStatus;
private int pageNum;
private int pageSize;
```

`KnowledgeNoteUpdateRequest`

```java
private String title;
private String tags;
private String distilledMd;
```

`KnowledgeNotePublishRequest`

```java
private Long categoryId;
private List<Long> tagIds;
private Integer status; // 0 草稿，1 发布
```

`KnowledgeNoteIndexRequest`

```java
private Long knowledgeBaseId;
private String knowledgeBaseName;
private String knowledgeBaseDescription;
```

规则：

```text
knowledgeBaseId 有值 → 使用已有知识库
knowledgeBaseId 为空且 knowledgeBaseName 有值 → 新建知识库
两者都空 → 参数错误
```

### 3.3 新增 VO

`KnowledgeNoteVO` 用于列表：

```java
private Long id;
private Long sourceDocumentId;
private Long blogPostId;
private Long knowledgeDocumentId;
private String title;
private String tags;
private String sourceType;
private String sourceUrl;
private String status;
private String publishStatus;
private String indexStatus;
private LocalDateTime createTime;
private LocalDateTime updateTime;
private LocalDateTime lastEditedAt;
private LocalDateTime lastPublishedAt;
private LocalDateTime lastIndexedAt;
```

`KnowledgeNoteDetailVO` 用于详情：

```java
private KnowledgeNoteVO note;
private SourceDocument source;
private String rawTextSummary;
private String rawText;
private String distilledMd;
private BlogPostVO blogPost;
private KnowledgeDocumentVO knowledgeDocument;
```

## 4. 服务拆分

### 4.1 `KnowledgeIngestionService`

职责：采集原文并写入 `source_document`。

建议接口：

```java
SourceDocument ingestUrl(KnowledgeIngestUrlRequest request, Long userId);

SourceDocument ingestFile(MultipartFile file, KnowledgeIngestFileRequest request, Long userId);

SourceDocument ingestAgentUrl(KnowledgeIngestUrlRequest request, Long userId);
```

输出要求：

```text
source_document.raw_text 有内容
source_document.source_type = URL / FILE / AGENT
source_document.status = SUCCESS
```

### 4.2 `KnowledgeDistillationService`

职责：原文到 Markdown 精读。

建议接口：

```java
KnowledgeNote distillToMarkdown(Long sourceDocumentId, Long userId);

KnowledgeNote redistill(Long noteId, Long userId);
```

`redistill` 行为：

```text
读取 source_document.raw_text
重新调用 AI
覆盖 distilled_md
更新 last_distilled_at
若已发博客 → publish_status = SYNC_REQUIRED
若已入知识库 → index_status = REINDEX_REQUIRED
```

### 4.3 `KnowledgeNoteService`

职责：精读工作台列表、详情、编辑和删除。

建议接口：

```java
Page<KnowledgeNoteVO> page(KnowledgeNoteQueryRequest request, Long userId);

KnowledgeNoteDetailVO getDetail(Long noteId, Long userId);

KnowledgeNoteVO update(Long noteId, KnowledgeNoteUpdateRequest request, Long userId);

boolean delete(Long noteId, Long userId);
```

保存编辑时：

```text
last_edited_at = now
update_time = now
如果 blog_post_id != null → publish_status = SYNC_REQUIRED
如果 knowledge_document_id != null → index_status = REINDEX_REQUIRED
```

### 4.4 `KnowledgeNotePublishService`

职责：精读稿发布到博客和同步博客。

建议接口：

```java
BlogPostVO publishToBlog(Long noteId, KnowledgeNotePublishRequest request, Long userId);

BlogPostVO syncToBlog(Long noteId, Long userId);
```

首次发布：

```text
如果 blog_post_id != null → 报错，提示使用 sync-blog
blog_post.title = note.title
blog_post.content = note.distilled_md
blog_post.summary = 从 Markdown 提取摘要
blog_post.status = request.status
blog_post.extendInfo = 溯源 JSON
note.blog_post_id = postId
note.publish_status = DRAFT_CREATED / PUBLISHED
note.last_published_at = now
```

`extendInfo` 建议：

```json
{
  "source": "knowledge_note",
  "sourceDocumentId": 123,
  "knowledgeNoteId": 456,
  "sourceUrl": "https://example.com/article",
  "generatedBy": "KnowledgeAI"
}
```

同步博客：

```text
覆盖 blog_post.title
覆盖 blog_post.content
更新 blog_post.updatedTime
根据 blog_post.status 回写 publish_status
更新 last_published_at
```

### 4.5 `KnowledgeNoteIndexService`

职责：精读稿加入知识库或重建索引。

建议接口：

```java
KnowledgeDocumentVO indexToKnowledgeBase(Long noteId, KnowledgeNoteIndexRequest request, Long userId);

KnowledgeDocumentVO reindex(Long noteId, Long userId);
```

首次入库：

```text
如果 knowledge_document_id != null → 报错，提示使用 reindex
如果 request.knowledgeBaseId 为空 → 新建知识库
创建 knowledge_document，fileType = MD
切块内容 = note.distilled_md
Embedding 后写入 knowledge_document_chunk
回写 note.knowledge_document_id
note.index_status = INDEXED
note.last_indexed_at = now
```

重建索引：

```text
找到 note.knowledge_document_id
删除旧 knowledge_document_chunk
使用 note.distilled_md 重新切块和 embedding
更新 knowledge_document.chunk_count
note.index_status = INDEXED
note.last_indexed_at = now
```

## 5. Controller 设计

### 5.1 采集接口

```http
POST /api/admin/knowledge/ingest/url
POST /api/admin/knowledge/ingest/file
```

URL 请求：

```json
{
  "url": "https://example.com/article",
  "title": "可选标题",
  "tags": "Spring,Java",
  "sourceType": "URL"
}
```

文件请求使用 `multipart/form-data`：

```text
file: MultipartFile
title: 可选
tags: 可选
```

### 5.2 精读笔记接口

```http
GET    /api/admin/knowledge/notes
GET    /api/admin/knowledge/notes/{noteId}
PUT    /api/admin/knowledge/notes/{noteId}
DELETE /api/admin/knowledge/notes/{noteId}
POST   /api/admin/knowledge/notes/{noteId}/redistill
```

### 5.3 博客发布接口

```http
POST /api/admin/knowledge/notes/{noteId}/publish-blog
POST /api/admin/knowledge/notes/{noteId}/sync-blog
```

### 5.4 知识库索引接口

```http
POST /api/admin/knowledge/notes/{noteId}/index
POST /api/admin/knowledge/notes/{noteId}/reindex
```

### 5.5 Agent 候选接口

```http
POST /api/admin/knowledge/search/preview
```

第一版返回学习大纲和手动候选结构，后续替换为真实搜索策略。

## 6. 实现阶段与验收

### 阶段 1：数据模型

- SQL 能执行。
- 旧 note 可查询。
- 新字段默认值正确。

### 阶段 2：采集 + 精炼

- URL 能生成 note。
- 文件能生成 note。
- 失败有明确错误。

### 阶段 3：预览编辑

- 列表能按关键词、来源、博客状态、知识库状态筛选。
- 详情能查看原文摘要和 Markdown。
- 保存编辑后能标记 `SYNC_REQUIRED` / `REINDEX_REQUIRED`。

### 阶段 4：发布博客

- 能发布草稿和直接发布。
- 已发布再次发布会报错。
- 同步博客会覆盖标题和正文。

### 阶段 5：加入知识库

- 能选择已有知识库或新建知识库。
- 能生成 `knowledge_document`。
- 能查看切块。
- RAG 问答能命中精读内容。

