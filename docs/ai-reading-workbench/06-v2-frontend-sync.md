# 06 V2 前端联调说明

> 产品两步：**搜索候选 → 勾选网页，按内置 Skill 出一篇 Markdown**。发博客/知识库可选。  
> 推荐搜索源：`deepseek`（官方联网，类网页版）。Tavily 仅备用。  
> **树模式**（带 `domainId` / `gatePassId` / `skipGate`）字段与联调顺序见 [08-v3-frontend-sync.md](./08-v3-frontend-sync.md)。

## 1. 配置

| 位置 | Key | 说明 |
| --- | --- | --- |
| YAML | `knowledge.deepseek.api-key` / `KNOWLEDGE_DEEPSEEK_API_KEY` | **DeepSeek 官方 Key**（≠ `knowledge.ai` DashScope） |
| YAML | `knowledge.deepseek.base-url` | 默认 `https://api.deepseek.com/anthropic` |
| YAML | `knowledge.deepseek.search-model` | 搜索候选 URL，默认 `deepseek-v4-flash` |
| YAML | `knowledge.deepseek.reading-model` | 勾选后读取网页材料，默认 `deepseek-v4-flash` |
| YAML | `knowledge.deepseek.distill-model` | 基于材料重构精读，默认 `deepseek-v4-pro` |
| `reading.search.provider` | `deepseek`（默认）/ `tavily` / `placeholder` | |
| `reading.distill.system_prompt` | 可选附加指令；空则只用内置 Skill | 不得覆盖 Skill 标题结构 |
| `reading.ingest.sync_mode` | 默认 **`async`**；调试可改 `sync` | |

集成设置也可覆盖 `knowledge.deepseek.api_key`。

**格式契约**：`GET /api/admin/knowledge/reading-skill` 返回 Skill 正文（`name` / `description` / `body`）。

**运维**：部署后执行建表 SQL `src/main/resources/sql/knowledge_reading_job_schema.sql`。

## 2. 搜索候选（步骤 1）

```http
POST /api/admin/knowledge/search/preview
```

```json
{ "goal": "想学 Redis 持久化", "preference": "官方文档优先" }
```

`data.candidates[]`：`title` / `url` / `summary` / `recommendReason` / `source`（多为 `deepseek`）/ `riskFlags`

交互：展示列表 → 用户勾选 → 再调批量合蒸。搜索阶段只返回候选 URL 与短说明，不缓存全文。

## 3. 勾选后按 Skill 出一篇（步骤 2）

```http
POST /api/admin/knowledge/ingest/batch-url
```

```json
{
  "urls": ["https://a", "https://b"],
  "sourceType": "AGENT",
  "agentQuery": "想学 Redis 持久化",
  "distillPrompt": "可选附加指令，不得覆盖 Skill 结构"
}
```

后端流程：

1. DeepSeek 读取勾选 URL，物化为多来源材料包；
2. 至少 1 篇可读即继续；
3. 按内置 Skill（`GET /api/admin/knowledge/reading-skill`）输出一篇 Markdown 精读。

Skill 规定标题：核心思想、关键知识点、代码/配置精髓、延伸问题、参考来源。

### 3.1 默认异步（`ingest.sync_mode=async`）

提交后**立即**返回任务视图（统一 VO：`KnowledgeReadingJobVO`），不阻塞到精读完成：

```json
{
  "jobId": 123,
  "status": "PENDING",
  "progress": "QUEUED",
  "success": false,
  "total": 2
}
```

前端每 **2s** 轮询：

```http
GET /api/admin/knowledge/reading-jobs/{jobId}
```

| `progress` | 建议文案 |
| --- | --- |
| `QUEUED` | 排队中 |
| `READING` | 正在读取网页 |
| `DISTILLING` | 正在重构精读 |
| `DONE` | 完成 |
| `ERROR` | 失败 |

| `status` | 前端动作 |
| --- | --- |
| `PENDING` / `RUNNING` | 继续轮询 |
| `SUCCESS` | 用 `noteId` 进预览；可读 `usedSources` / `failedSources` |
| `FAILED` | 展示 `errorMsg` |

成功响应字段示例：`jobId`、`status=SUCCESS`、`progress=DONE`、`noteId`、`title`、`usedCount`、`failCount`、`warning`、`usedSources`、`failedSources`。

Worker 全局串行：同时最多 1 个 `RUNNING`；排队任务保持 `PENDING`。单任务超过约 20 分钟会标 `FAILED`（超时）。

第一版不做取消、不做 SSE。

### 3.2 同步调试（`ingest.sync_mode=sync`）

一次请求直接返回同一 VO，且 `status=SUCCESS`、`progress=DONE`、带 `noteId`（无 `jobId` 落库也可）。仅建议本地/排障使用；单 URL/文件采集仍强制 sync。

`usedSources[].bodyChars` 表示该来源读到的材料字数；`failedSources` 表示 AI 未能读取或材料过短的来源。

## 4. 博客 / 知识库（可选，精读完成后）

对得到的 `noteId`：

- `POST /notes/{id}/publish-blog` / `sync-blog`
- `POST /notes/{id}/index`

## 5. 前端清单

- [ ] 配置引导：DeepSeek 官方 Key（与知识库 AI Key 分开）
- [ ] `GET /reading-skill` 可展示输出格式（可选）
- [ ] 搜索列表预览勾选
- [ ] `POST batch-url` 取 `jobId`，每 2s 轮询 `GET /reading-jobs/{jobId}`
- [ ] 按 `progress` 展示：排队 / 读取网页 / 按 Skill 写精读 / 完成
- [ ] `SUCCESS` → `noteId` 预览；`FAILED` → `errorMsg`
- [ ] （可选）站点设置切 `sync` 做联调回归
- [ ] 发布博客 / 入库按钮仍接现有接口
