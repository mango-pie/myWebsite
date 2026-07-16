# 03 V1 前端对接文档（给前端）

> Ai-Backend 新功能第一版前端。请优先阅读本文；总览见 [00-overview.md](./00-overview.md)，接口细节见 [02-v1-backend.md](./02-v1-backend.md)。  
> 本文只覆盖工作台页面与交互；现有知识库 RAG 页面仍见 `docs/KNOWLEDGE_FRONTEND_SYNC.md`。

## 1. 前端目标

前端需要提供一个独立的管理员工作台，让管理员完成：

```text
采集 URL / 文件 / Agent 候选
  ↓
查看 AI 精读稿
  ↓
编辑标题、标签、Markdown
  ↓
发布博客或加入知识库
  ↓
后续同步博客或重建索引
```

该模块应独立于现有博客编辑页和知识库文档页，但需要能跳转到它们。

## 2. 信息架构

后台菜单建议：

```text
后台管理
  └── AI 精读工作台
        ├── 内容采集
        ├── 精读列表
        └── 精读详情
```

路由建议：

```text
/admin/knowledge/ingest
/admin/knowledge/notes
/admin/knowledge/notes/:noteId
```

后续真实 Agent 搜索不新增独立页面，优先作为采集页第三个 Tab。

## 3. 页面 1：内容采集

路由：

```text
/admin/knowledge/ingest
```

### 3.1 Tab 结构

```text
URL
文件
AI 搜索
```

### 3.2 URL Tab

字段：

```text
文章 URL：必填
标题：可选
标签：可选，逗号分隔
```

按钮：

```text
开始精炼
```

成功后跳转：

```text
/admin/knowledge/notes/:noteId
```

### 3.3 文件 Tab

字段：

```text
文件：必填，支持 PDF/DOCX/TXT/MD
标题：可选，默认文件名
标签：可选
```

按钮：

```text
上传并精炼
```

### 3.4 AI 搜索 Tab（第一版半自动）

第一版不接真实搜索 API，做半自动候选：

字段：

```text
学习目标：必填
候选 URL：多行输入，每行一个 URL
偏好说明：可选
```

可选体验：

```text
生成学习大纲
```

学习大纲第一版可由后端 mock，或前端根据输入展示占位说明。

候选 URL 列表展示：

```text
URL
标题（可手动填写或从 URL 默认）
操作：抓取并精炼
```

点击「抓取并精炼」时：

```text
调用 /api/admin/knowledge/ingest/url
sourceType = AGENT
```

## 4. 页面 2：精读列表

路由：

```text
/admin/knowledge/notes
```

### 4.1 筛选区

第一版筛选做四类：

```text
关键词：标题 / URL / 标签
来源：全部 / URL / FILE / AGENT
博客状态：全部 / 未发布 / 草稿 / 已发布 / 需同步 / 同步失败
知识库状态：全部 / 未入库 / 已入库 / 需重建 / 入库失败
```

### 4.2 列表字段

```text
标题
来源类型
标签
博客状态
知识库状态
更新时间
操作
```

### 4.3 操作按钮规则

| 条件 | 操作 |
| --- | --- |
| 任意状态 | 预览 |
| 精读失败 | 重新蒸馏、删除 |
| 未发布博客 | 发布博客 |
| 已发博客且需同步 | 同步博客 |
| 未入知识库 | 加入知识库 |
| 已入库且需重建 | 重建索引 |
| 不再处理 | 删除 / 放弃 |

状态建议用多个 Tag 展示，不要合成一个复杂状态。

## 5. 页面 3：精读详情

路由：

```text
/admin/knowledge/notes/:noteId
```

### 5.1 页面结构

```text
顶部：
  标题
  来源类型
  来源链接
  博客状态 Tag
  知识库状态 Tag

正文：
  左侧 Markdown 编辑器
  右侧 Markdown 预览

侧边信息：
  原文摘要
  查看完整原文按钮
  博客关联
  知识库文档关联

底部操作：
  保存修改
  重新蒸馏
  发布博客
  同步博客
  加入知识库
  重建索引
```

### 5.2 原文展示

详情页默认只展示原文摘要。

点击「查看完整原文」后弹窗展示 `raw_text`，避免长文拖慢页面。

### 5.3 保存修改

可编辑字段：

```text
标题
标签
Markdown 正文
```

保存后前端提示：

```text
内容已保存。
如果该精读已发布博客，需要同步博客。
如果该精读已加入知识库，需要重建索引。
```

具体状态以后端返回为准：

```text
publishStatus = SYNC_REQUIRED
indexStatus = REINDEX_REQUIRED
```

### 5.4 重新蒸馏

由于重新蒸馏会直接覆盖当前 Markdown，前端必须二次确认：

```text
重新蒸馏会覆盖当前 Markdown 内容。
如果你已手动修改，修改内容会丢失。
是否继续？
```

第一版不支持自定义指令。后续可增加：

```text
补充要求：更适合博客发布 / 更适合面试复习 / 增加代码示例
```

## 6. 发布博客交互

触发按钮：

```text
发布博客
```

弹窗字段：

```text
分类
标签
发布状态：草稿 / 直接发布
```

成功后弹窗询问：

```text
博客已创建，是否跳转到博客编辑器？
```

按钮：

```text
留在当前页
打开博客编辑器
```

如果已有关联博客，再次发布时后端会报错，前端提示使用「同步博客」。

## 7. 同步博客交互

触发条件：

```text
publishStatus = SYNC_REQUIRED
```

按钮：

```text
同步博客
```

二次确认：

```text
同步会覆盖博客文章的标题和正文。
是否继续？
```

成功后：

```text
publishStatus 回到 DRAFT_CREATED 或 PUBLISHED
lastPublishedAt 更新
```

## 8. 加入知识库交互

触发按钮：

```text
加入知识库
```

弹窗字段：

```text
知识库来源：
  - 选择已有知识库
  - 新建知识库

选择已有：
  - 知识库下拉框

新建知识库：
  - 名称
  - 描述
```

成功后留在当前页面，并显示：

```text
打开知识库文档
```

该按钮跳转到知识库文档详情或对应知识库页面。

## 9. 重建索引交互

触发条件：

```text
indexStatus = REINDEX_REQUIRED
```

按钮：

```text
重建索引
```

二次确认：

```text
重建索引会删除旧切块并重新向量化当前 Markdown。
是否继续？
```

成功后：

```text
indexStatus = INDEXED
lastIndexedAt 更新
```

## 10. 前端 API 模块

建议新建独立模块：

```text
src/api/knowledgeNote.ts
```

方法：

```ts
ingestUrl()
ingestFile()
searchPreview()
listNotes()
getNoteDetail()
updateNote()
redistillNote()
deleteNote()
publishBlog()
syncBlog()
indexNote()
reindexNote()
```

不要把精读工作台逻辑塞进现有知识库聊天 API 中。知识库文档管理和精读工作台的业务边界不同。

## 11. V1 Agent Tab 说明

V1 只做半自动候选（手动 URL）。真实搜索与学习路线见：

- [04-v2-search-batch.md](./04-v2-search-batch.md)
- [05-v3-agent-path.md](./05-v3-agent-path.md)

## 12. MVP 前端验收

- 管理员能从 URL 生成精读 note。
- 管理员能从文件生成精读 note。
- 管理员能查看精读列表并筛选。
- 管理员能在详情页编辑 Markdown 并保存。
- 管理员能重新蒸馏并覆盖当前内容。
- 管理员能发布博客草稿或直接发布。
- 发布成功后能选择是否跳转博客编辑器。
- 管理员能加入已有知识库或新建知识库。
- 入库成功后能打开知识库文档。
- 修改已发布/已入库 note 后，页面能显示需同步或需重建。
- 半自动 Agent Tab 能输入候选 URL 并选择一条进入精炼链路。

