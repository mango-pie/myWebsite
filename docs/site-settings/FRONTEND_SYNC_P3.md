# 全站设置中心 P3 — 前端对接说明（知识库 / 精读）

> 依赖 P0；建议已完成 P1。后端已实现 `knowledge`、`reading` 行为参数模块。  
> 产品说明见 [05-p3-knowledge-reading.md](./05-p3-knowledge-reading.md)。  
> **密钥 / baseUrl** 仍在 `integration`，本页不重复配置。

## 1. 侧栏

在「语音 TTS」之后增加：

| 模块 | 路由建议 | displayName |
| --- | --- | --- |
| `knowledge` | `/admin/settings/knowledge` | 知识库 / RAG |
| `reading` | `/admin/settings/reading` | 精读工作台 |

`GET /api/admin/site-settings/modules` 中两者 `writable=true`、`phase=P3`。

## 2. API（沿用 P0）

| 方法 | 路径 |
| --- | --- |
| GET | `/api/admin/site-settings/{module}/schema` |
| GET | `/api/admin/site-settings/{module}` |
| PUT | `/api/admin/site-settings/{module}` |
| POST | `/api/admin/site-settings/{module}/reset` |

`module` = `knowledge` | `reading`。无需新接口。

## 3. 表单分组建议

### knowledge

1. **模型运行参数**：`ai.chat_model` / `embedding_model` / `temperature` / `max_tokens` / `timeout_seconds`
2. **RAG**：`rag.top_k` / `chunk_size` / `chunk_overlap`
3. **Jina**：`jina.timeout_seconds`

### reading

1. **蒸馏**：`distill.temperature` / `max_tokens` / `system_prompt`（TextArea）
2. **发布与确认**：`publish.default_as_draft` / `ask_open_editor` / `redistill.confirm_required`
3. **高级（可折叠）**：`search.provider` / `ingest.sync_mode`

## 4. 危险项与 sideEffect

schema 字段可能含：

- `danger: true`
- `sideEffect: "变更后需对已有文档手动重建索引，不会自动重建"`

以下字段需二次 Confirm：

- `ai.embedding_dimension`
- `rag.chunk_size`
- `rag.chunk_overlap`

后端**不会**自动删向量或重建索引。

## 5. 前端 UX 字段（读设置，不拦 API）

| Key | 用法 |
| --- | --- |
| `publish.default_as_draft` | 发布博客表单默认是否草稿；后端在 `status` 未传时也会按此默认 |
| `publish.ask_open_editor` | 发布成功后是否弹「打开编辑器」 |
| `redistill.confirm_required` | 重新蒸馏按钮前是否强制 Confirm |
| `search.provider` | 搜索预览策略；推荐 `deepseek`（官方联网），也可 `tavily` / `placeholder` |
| `ingest.sync_mode` | 仅 `sync` 可用；设为 `async` 时采集接口返回业务错误 |

精读工作台业务页**不要**再复制一套设置表单，统一跳转 `/admin/settings/reading`。

## 6. 密钥引导

知识库 AI、Jina、Tavily、DeepSeek 联网、MinIO 等连接信息 → 「集成与密钥」`/admin/settings/integration`。

V2 搜索与批量精炼 API 见 [../ai-reading-workbench/06-v2-frontend-sync.md](../ai-reading-workbench/06-v2-frontend-sync.md)。

## 7. Key 清单

完整契约见 [01-module-keys.md](./01-module-keys.md) 第 8～9 节。
