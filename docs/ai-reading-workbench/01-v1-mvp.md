# 01 V1 MVP：拆解与验收清单

> Ai-Backend 新功能第一版。总览见 [00-overview.md](./00-overview.md)。

## 1. MVP 目标

第一版只解决一条主线：

```text
管理员从 URL / 文件 / 半自动 Agent 候选中获取资料
  ↓
AI 精炼为 Markdown
  ↓
管理员预览和编辑
  ↓
发布到博客或加入知识库
```

不追求完整 Agent 自动化，不引入异步任务，不做版本历史。

## 2. 阶段 1：数据模型

### 开发项

- 更新 `knowledge_note` 表结构。
- 更新 `KnowledgeNote` 实体。
- 增加 note 查询、更新、发布、索引相关 DTO/VO。
- 确认旧数据默认状态可用。

### 验收

- 应用启动无实体映射错误。
- 新 note 默认：
  - `publish_status = NOT_PUBLISHED`
  - `index_status = NOT_INDEXED`
- 旧 note 可在列表中展示。

## 3. 阶段 2：采集与精炼

### 开发项

- `POST /api/admin/knowledge/ingest/url`
- `POST /api/admin/knowledge/ingest/file`
- URL 使用 Jina Reader 抓取正文。
- 文件复用已有文档读取能力提取正文。
- 提取正文后调用 AI 精炼，生成 `knowledge_note`。

### 验收

- URL 能生成 `source_document` 和 `knowledge_note`。
- 文件能生成 `source_document` 和 `knowledge_note`。
- `source_document.raw_text` 保存原文。
- `knowledge_note.distilled_md` 保存 Markdown。
- 失败时返回明确错误，不能产生半截脏数据。

## 4. 阶段 3：精读工作台后端

### 开发项

- `GET /api/admin/knowledge/notes`
- `GET /api/admin/knowledge/notes/{noteId}`
- `PUT /api/admin/knowledge/notes/{noteId}`
- `DELETE /api/admin/knowledge/notes/{noteId}`
- `POST /api/admin/knowledge/notes/{noteId}/redistill`

### 验收

- 列表支持关键词、来源、博客状态、知识库状态筛选。
- 详情包含 note、source、原文摘要、完整原文、Markdown。
- 保存修改后 `last_edited_at` 更新。
- 已发布博客的 note 修改后变为 `SYNC_REQUIRED`。
- 已入库的 note 修改后变为 `REINDEX_REQUIRED`。
- 重新蒸馏会覆盖当前 Markdown。

## 5. 阶段 4：发布博客与同步

### 开发项

- `POST /api/admin/knowledge/notes/{noteId}/publish-blog`
- `POST /api/admin/knowledge/notes/{noteId}/sync-blog`
- 发布时复用 `BlogPostService.addBlogPost`。
- 同步时覆盖博客标题和正文。
- `blog_post.extendInfo` 写入来源信息。

### 验收

- 能发布博客草稿。
- 能直接发布博客。
- 发布后 `knowledge_note.blog_post_id` 回写。
- 发布后 `publish_status` 正确：
  - 草稿：`DRAFT_CREATED`
  - 发布：`PUBLISHED`
- 已发布过的 note 再调用 publish 会报错。
- 修改 note 后调用 sync 能覆盖博客标题和正文。

## 6. 阶段 5：加入知识库与重建索引

### 开发项

- `POST /api/admin/knowledge/notes/{noteId}/index`
- `POST /api/admin/knowledge/notes/{noteId}/reindex`
- 支持选择已有知识库。
- 支持新建知识库。
- 创建 `knowledge_document`，`file_type = MD`。
- 使用 `knowledge_note.distilled_md` 切块、Embedding、写 pgvector。

### 验收

- 入库后 `knowledge_note.knowledge_document_id` 回写。
- 入库后 `index_status = INDEXED`。
- 知识库文档列表能看到精读生成的 MD 文档。
- 文档切块可查看。
- 知识库问答能检索到该精读内容。
- 修改 note 后 reindex 能删除旧 chunk 并重建。

## 7. 阶段 6：前端工作台

### 开发项

- `/admin/knowledge/ingest`
- `/admin/knowledge/notes`
- `/admin/knowledge/notes/:noteId`
- Markdown 编辑器 + 预览左右分栏。
- 原文摘要 + 完整原文弹窗。
- 发布博客弹窗。
- 加入知识库弹窗。
- 同步博客和重建索引按钮。

### 验收

- 管理员能通过 URL 完成完整链路。
- 管理员能通过文件完成完整链路。
- 发布博客后弹窗询问是否跳转博客编辑器。
- 入库后保留在详情页，并显示打开知识库文档按钮。
- 修改已发布/已入库 note 后，页面能看到需同步/需重建提示。

## 8. 阶段 7：半自动 Agent Tab

### 开发项

- 采集页新增 `AI 搜索` Tab。
- 输入学习目标。
- 手动输入多个候选 URL。
- 可展示非落库学习大纲。
- 选择单篇 URL 进入 `ingest/url`。
- 进入链路时 `source_type = AGENT`。

### 验收

- 能输入学习目标和候选 URL。
- 能选择一条 URL 生成 AGENT 来源 note。
- AGENT 来源 note 后续可发布博客或加入知识库。

## 9. 暂缓项

以下功能不进入 MVP：

- 真实搜索 API。
- 多选批量抓取和精炼。
- Agent 自动抓取前 N 篇文章。
- 任务异步队列。
- 蒸馏版本历史。
- Markdown diff。
- 博客同步 diff。
- 知识库索引版本化。
- 学习路线落库。
- 多模型选择。

## 10. 最终验收脚本

### 脚本 A：URL 到博客

1. 管理员进入内容采集页。
2. 输入 URL 并开始精炼。
3. 进入精读详情页。
4. 修改标题和 Markdown。
5. 发布为博客草稿。
6. 打开博客编辑器，确认标题和正文正确。

### 脚本 B：URL 到知识库

1. 输入 URL 并生成精读。
2. 在详情页选择已有知识库或新建知识库。
3. 加入知识库。
4. 打开知识库文档，确认文档和切块存在。
5. 在知识库问答中提问，确认能命中精读内容。

### 脚本 C：修改后的同步与重建

1. 对已发布博客且已入库的 note 修改 Markdown。
2. 确认博客状态变为 `SYNC_REQUIRED`。
3. 确认知识库状态变为 `REINDEX_REQUIRED`。
4. 执行同步博客。
5. 执行重建索引。
6. 确认状态恢复正常。

### 脚本 D：半自动 Agent

1. 进入 AI 搜索 Tab。
2. 输入学习目标。
3. 手动填入多个候选 URL。
4. 选择一条抓取并精炼。
5. 确认生成的 note 来源为 `AGENT`。
6. 后续发布博客或加入知识库。

