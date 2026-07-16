# 00 AI 精读工作台总览

> 所属项目：Ai-Backend（本仓库新功能）  
> 不是原 Knowledge AI 独立仓库的 V1～V5 规划文档。

## 1. 定位

把「发现优质资料、快速精炼、人工确认、沉淀到博客和知识库」做成一条可复用链路，提高学习效率，并为后续 Agent 搜文留统一入口。

核心流程：

```text
URL / 文件 / AI 搜索候选
  ↓
抓取或解析原文 → source_document
  ↓
AI 精炼 Markdown → knowledge_note
  ↓
管理员预览、编辑、重新蒸馏
  ↓
发布到博客 / 加入知识库 / 两者都做
```

## 2. 设计原则

- **先预览，后入库**：精读稿先进入 `knowledge_note`，确认后再写博客或向量库。
- **博客与知识库解耦**：`blog_post` 对外展示，知识库做 RAG，`knowledge_note` 是中间工作稿。
- **索引精炼稿**：入知识库时向量化 `distilled_md`；原文留在 `source_document.raw_text` 供溯源。
- **仅管理员（V1）**：Session + `@AuthCheck`。
- **Agent 只做输入源**：不自动发博客、不自动入库；选中后再走同一条采集链。

## 3. 已确认产品决策（摘要）

| 项 | 决策 |
| --- | --- |
| 入口 | 统一采集页：URL / 文件 / AI 搜索 三个 Tab |
| 流水线 | 先精炼，后分流（2-A） |
| 预览 | 精炼稿为主；原文摘要 + 完整原文弹窗 |
| 编辑 | 标题、标签、Markdown；编辑器 + 预览左右分栏 |
| 重新蒸馏 | 直接覆盖；前端二次确认 |
| 博客 | 草稿 / 直接发布；发布后询问是否跳转编辑器 |
| 同步博客 | 手动同步，覆盖标题和正文 |
| 知识库 | 可选已有库或新建；生成 `knowledge_document`（MD） |
| 重建索引 | 删旧 chunk 后重建 |
| V1 Agent | 手动多候选 URL，单篇抓取；可展示学习大纲（不落库） |
| 执行方式 | V1 同步，暂无任务队列 |

## 4. 业务对象

```text
source_document          原文与来源（URL / FILE / AGENT）
knowledge_note           精炼稿 + 博客/入库状态
blog_post                正式博客文章
knowledge_document       精炼稿对应的知识库虚拟 MD 文档
knowledge_document_chunk 切块 + embedding（pgvector）
```

## 5. 版本路线

### V1（当前要做）

- URL + 文件采集并精炼。
- 精读列表 / 详情 / 编辑 / 删除 / 重新蒸馏。
- 发布博客（草稿或直接发布）+ 同步博客。
- 加入知识库 + 重建索引。
- 半自动 Agent Tab（手动候选 URL）。

详见：[01-v1-mvp.md](./01-v1-mvp.md)、[02-v1-backend.md](./02-v1-backend.md)、[03-v1-frontend.md](./03-v1-frontend.md)。

### V2（下一版）

- 接入真实搜索 API（Tavily / Bocha 等）。
- 搜索候选多选 + 批量抓取精炼。
- 仍须用户确认，不自动入库发博客。

详见：[04-v2-search-batch.md](./04-v2-search-batch.md)。

### V3（远期）

- 学习主题 → Agent 生成学习大纲并落库。
- 按章节搜索资料并批量进入精读链。
- 专题归档与学习路径。

详见：[05-v3-agent-path.md](./05-v3-agent-path.md)。

## 6. V1 明确不做

- 真实联网搜索、批量自动精炼。
- 异步任务队列、版本历史、Markdown/博客 diff。
- 索引版本化、学习路线落库、多模型选择面板。
