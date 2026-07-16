# Ai-Backend Knowledge AI 集成说明

本项目已将 `knowledge-ai-v2` 的知识库、RAG、AI 问答能力以内置模块方式集成到 `Ai-Backend`，不替换现有聊天、博客、学习、TTS 等业务。

## 接口前缀

因为 `Ai-Backend` 已配置 `server.servlet.context-path=/api`，以下路径实际访问时均带 `/api` 前缀。

| 能力 | 路径 |
| --- | --- |
| 知识库 CRUD | `/kb/knowledge-bases` |
| 文档上传/列表 | `/kb/knowledge-bases/{kbId}/documents` |
| 文档详情/删除/下载 | `/kb/documents/{id}` |
| 文档解析 | `/kb/documents/{id}/parse` |
| Chunk 查看 | `/kb/documents/{id}/chunks` |
| 知识库会话 | `/kb/conversations` |
| 非流式问答 | `/kb/chat` |
| SSE 流式问答 | `/kb/chat/stream` |
| URL 入库/精读预留 | `/admin/knowledge/**` |

## 配置

新增配置位于 `src/main/resources/application.yml`：

- `knowledge.minio.*`
- `knowledge.vector.datasource.*`
- `knowledge.ai.*`
- `knowledge.jina.*`
- `knowledge.rag.*`

密钥建议通过环境变量注入：

```bash
KNOWLEDGE_AI_API_KEY=你的模型服务 Key
KNOWLEDGE_AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
KNOWLEDGE_AI_CHAT_MODEL=deepseek-v3
KNOWLEDGE_AI_EMBEDDING_MODEL=text-embedding-v2
KNOWLEDGE_AI_EMBEDDING_DIMENSION=1536
```

## 数据库

MySQL 执行：

```bash
mysql -uroot -proot aiscene < src/main/resources/sql/knowledge_schema.sql
```

若库中已有 `knowledge_note` 表，执行 V1 增量脚本：

```bash
mysql -uroot -proot aiscene < src/main/resources/sql/knowledge_note_v1_alter.sql
```

PostgreSQL + pgvector 执行：

```bash
psql -U kai -d knowledge_ai -f src/main/resources/sql/knowledge_pgvector_schema.sql
```

## 前端联调要点

完整对接说明见：[KNOWLEDGE_FRONTEND_SYNC.md](./KNOWLEDGE_FRONTEND_SYNC.md)（类型、页面路由、SSE 示例、联调清单）。

摘要：

- 所有接口沿用现有 Session 登录态，不需要 JWT。
- 文档上传字段名为 `file`，支持 PDF、DOCX、TXT、MD/Markdown，单文件最大 50MB。
- 上传后调用 `/kb/documents/{id}/parse` 才会解析、切块、向量化。
- `/kb/chat/stream` 返回 SSE 事件：
  - `message`：增量文本
  - `done`：完成（含完整 `KnowledgeChatResponse` JSON）
  - `error`：异常
- 引用来源保存到消息历史，前端在 `done` 后也可刷新 `/kb/conversations/{id}/messages` 获取。

## 双引擎扩展预留

已预留以下表与服务接口，用于后续实现“RAG + AI 精读”：

- `source_document`：URL/FILE/AGENT 统一源文档
- `knowledge_note`：AI 精读 Markdown
- `knowledge_processing_task`：后续异步任务与重试
- `KnowledgeIngestionService`
- `KnowledgeDistillationService`
- `KnowledgeRagIndexService`
- `KnowledgeSearchStrategy`

精读内容先进入 `knowledge_note.distilled_md`，后续人工确认后再关联或发布到博客表，避免影响现有博客业务。

## AI 精读工作台（本仓库新功能）

原 `00`～`06` 为 Knowledge AI 独立项目规划。**精读 → 博客 / 知识库** 的新功能文档已独立存放：

- [ai-reading-workbench/README.md](./ai-reading-workbench/README.md)
- 总览：[00-overview.md](./ai-reading-workbench/00-overview.md)
- V1 后端：[02-v1-backend.md](./ai-reading-workbench/02-v1-backend.md)
- V1 前端：[03-v1-frontend.md](./ai-reading-workbench/03-v1-frontend.md)
- V2 搜索/批量：[04-v2-search-batch.md](./ai-reading-workbench/04-v2-search-batch.md)
- V2 前端联调：[06-v2-frontend-sync.md](./ai-reading-workbench/06-v2-frontend-sync.md)
