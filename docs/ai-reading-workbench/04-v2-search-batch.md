# 04 V2：真实搜索与批量精炼

> **精读两步**：搜索候选 → 勾选 URL，按内置 Skill 出一篇 Markdown。  
> 发博客 / 入知识库是可选后续，不是精读必需。联调见 [06-v2-frontend-sync.md](./06-v2-frontend-sync.md)。

## 产品两步

1. 告诉 AI 要学什么 → 联网搜索 → 列表勾选  
2. 对勾选 URL：读取材料后，**严格按照** `skills/knowledge-reading/SKILL.md` 输出一篇 Markdown，写入 `knowledge_note`

可选：人工改稿后发博客 / 入知识库 / 挂领域树。

格式契约：`src/main/resources/skills/knowledge-reading/SKILL.md`（`GET /admin/knowledge/reading-skill` 可取回正文）。  
`distillPrompt` / 站点 `distill.system_prompt` 只作**附加指令**，不能覆盖 Skill 的标题结构与硬性约束。

Tavily 仍可作为 `search.provider=tavily` 备用，默认应为 `deepseek`。
搜索与读页使用 `search-model` / `reading-model`（默认均为 `deepseek-v4-flash`）；精读重构使用 `distill-model`（默认 `deepseek-v4-pro`）。

## 异步合蒸（默认）

- `reading.ingest.sync_mode` 默认 `async`：`POST /ingest/batch-url` 立即返回 `jobId`，前端轮询 `GET /reading-jobs/{jobId}`。
- Worker `@Scheduled` 每 3s 抢一条 PENDING，全局同时最多 1 个 RUNNING；表 `knowledge_reading_job`。
- 进度：`QUEUED` → `READING` → `DISTILLING` → `DONE` / `ERROR`。联调细节见 [06](./06-v2-frontend-sync.md)。
- 调试可改 `sync`：同接口一次返回 `SUCCESS` + `noteId`。

## 验收

- 配置 DeepSeek 官方 Key 后能出文章候选 URL  
- 勾选后只生成一篇 note，且 Markdown 含 Skill 规定标题（核心思想 / 关键知识点 / 代码/配置精髓 / 延伸问题 / 参考来源）  
- 合蒸不再先逐篇摘要；`SourceDocument.rawText` 保存拼接后的材料包  
- 该 note 可发博客、可入知识库  
- `distillPrompt` 或站点附加指令只影响语气/侧重点，不改变 Skill 结构  
- `async`：提交立即有 `jobId`，轮询至 `SUCCESS` 且有 `noteId`；两任务时第二个保持 PENDING 直到第一个结束  
- `sync`：一次请求直接拿到 `noteId`（回归） 
