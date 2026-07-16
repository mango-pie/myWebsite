# Knowledge AI 前端对接入口

本文档对应 **已集成到 Ai-Backend 的知识库基础能力**（知识库、文档、切块、RAG 问答）。

**AI 精读工作台（采集 → 精炼 → 发博客 / 入知识库）** 是本仓库新功能，前端请改读：

→ [ai-reading-workbench/03-v1-frontend.md](./ai-reading-workbench/03-v1-frontend.md)

→ 目录：[ai-reading-workbench/README.md](./ai-reading-workbench/README.md)

---

## 1. 基础约定

- 后端全局 context-path：`/api`
- 知识库接口前缀：`/api/kb/**`
- 鉴权：Session Cookie，`credentials: 'include'`

## 2. 已有知识库接口摘要

| 能力 | 接口 |
| --- | --- |
| 知识库列表 | `GET /api/kb/knowledge-bases` |
| 创建知识库 | `POST /api/kb/knowledge-bases` |
| 文档列表 | `GET /api/kb/knowledge-bases/{kbId}/documents` |
| 上传文档 | `POST /api/kb/knowledge-bases/{kbId}/documents`，字段名 `file` |
| 解析文档 | `POST /api/kb/documents/{id}/parse` |
| 查看切块 | `GET /api/kb/documents/{id}/chunks?pageNum=1&pageSize=20` |
| 同步问答 | `POST /api/kb/chat` |
| SSE 问答 | `POST /api/kb/chat/stream` |

SSE 事件：`message` / `done` / `error`。

## 3. 页面边界

| 页面 | 文档 |
| --- | --- |
| 知识库 / RAG 聊天 | 本文 |
| AI 精读工作台 | `ai-reading-workbench/03-v1-frontend.md` |
| 博客编辑器 | 现有博客模块 |
