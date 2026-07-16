# 06 V2 前端联调说明

> 产品四步：**学什么 → DeepSeek 联网找页 → 列表勾选 → 按提示词合蒸一篇 → 博客/知识库**。  
> 推荐搜索源：`deepseek`（官方联网，类网页版）。Tavily 仅备用。

## 1. 配置

| 位置 | Key | 说明 |
| --- | --- | --- |
| YAML | `knowledge.deepseek.api-key` / `KNOWLEDGE_DEEPSEEK_API_KEY` | **DeepSeek 官方 Key**（≠ `knowledge.ai` DashScope） |
| YAML | `knowledge.deepseek.base-url` | 默认 `https://api.deepseek.com/anthropic` |
| YAML | `knowledge.deepseek.search-model` | 搜索候选 URL，默认 `deepseek-v4-flash` |
| YAML | `knowledge.deepseek.reading-model` | 勾选后读取网页材料，默认 `deepseek-v4-flash` |
| YAML | `knowledge.deepseek.distill-model` | 基于材料重构精读，默认 `deepseek-v4-pro` |
| `reading.search.provider` | `deepseek`（默认）/ `tavily` / `placeholder` | |
| `reading.distill.system_prompt` | 控制最终精读结构 | 只作用于「材料拼接后」的重构阶段 |
| `reading.ingest.sync_mode` | 须 `sync` | |

集成设置也可覆盖 `knowledge.deepseek.api_key`。

## 2. 搜索候选（步骤 1～2）

```http
POST /api/admin/knowledge/search/preview
```

```json
{ "goal": "想学 Redis 持久化", "preference": "官方文档优先" }
```

`data.candidates[]`：`title` / `url` / `summary` / `recommendReason` / `source`（多为 `deepseek`）/ `riskFlags`

交互：展示列表 → 用户勾选 → 再调批量合蒸。搜索阶段只返回候选 URL 与短说明，不缓存全文。

## 3. 勾选后合蒸一篇（步骤 3）

```http
POST /api/admin/knowledge/ingest/batch-url
```

```json
{
  "urls": ["https://a", "https://b"],
  "sourceType": "AGENT",
  "agentQuery": "想学 Redis 持久化",
  "distillPrompt": "可选：本次覆盖 reading.distill.system_prompt"
}
```

后端流程：

1. DeepSeek 读取勾选 URL，物化为多来源 Markdown 材料包；
2. 至少 1 篇可读即继续；
3. 将材料包拼接后，用固定硬约束 + `distillPrompt`（或 `reading.distill.system_prompt`）一次重构成一篇 Markdown 精读。

返回单个 `noteId`（另有 `usedSources` / `failedSources`）。`usedSources[].bodyChars` 表示该来源读到的材料字数；`failedSources` 表示 AI 未能读取或材料过短的来源。该步骤可能比搜索更慢，前端建议展示「正在读取网页并重构精读」。

## 4. 博客 / 知识库（步骤 4）

对得到的 `noteId`：

- `POST /notes/{id}/publish-blog` / `sync-blog`
- `POST /notes/{id}/index`

## 5. 前端清单

- [ ] 配置引导：DeepSeek 官方 Key（与知识库 AI Key 分开）
- [ ] 搜索列表预览勾选
- [ ] batch-url 按单篇 note 处理，并支持可选 `distillPrompt`
- [ ] 生成中 loading 文案：读取网页 + 重构精读
- [ ] 发布博客 / 入库按钮仍接现有接口
