# P2：档案库 AI 补强 + 运维实时日志流（2026-09-14）

> 本次变更已在本地完成开发、打包与端到端冒烟（含临时 admin 账号实测，测试数据已清理）。
> 文档作为交接存档，说明改动点、新接口与运维注意事项。

## 一、运维实时日志流（AstrBot 式）

**能力**：Admin 前端订阅 SSE，实时滚动系统日志 + 审计事件；连接时回放最近 500 条缓冲，15s 心跳保活。

| 文件 | 说明 |
|---|---|
| `com/ai/log/LogStreamAppender` | Logback appender，挂根 logger，格式化事件转发 sink（append 内零日志防递归） |
| `com/ai/config/OpsLogStreamConfig` | 启动挂载 / 销毁卸载 appender |
| `com/ai/service/ops/OpsLogStreamService` | 环形缓冲 500 条 + SSE 广播 + 心跳；`@ConditionalOnModule("ops")` |
| `OpsAuditLogServiceImpl.record` | 审计落库前双写广播（detail 已走脱敏） |
| `AdminOpsController` | `GET /admin/ops/logs/stream?debug=true`（SSE，`@AuthCheck(admin)`；`debug=true` 收 DEBUG 级） |

事件协议：`event: log`（data 为 JSON：time/level/thread/logger/message/throwable?/traceId?/kind）、`event: ready`、`event: ping`。
审计事件 `kind=audit`、logger 固定 `AUDIT`。

## 二、档案库 AI 摘要补缺：PDF / DOCX

- `LibraryServiceImpl`：新增 `AI_DOC_EXTS = {pdf, docx}`；`aiEligible` 三集合并集。
- 解析路径：`KnowledgeDocumentReaderService.read(bytes, ext)`（复用知识库 PDFBox/POI 解析器，需 knowledge 模块开启；未开启时摘要失败并给出明确 aiError）。
- doc（老版 Word）仍不支持；摘要文本上限仍为 8000 字符。

## 三、档案库语义搜索

**链路**：AI 摘要完成 → `文件名\n摘要` embed（DashScope text-embedding-v2, 1536 维）→ pgvector 落库 → 查询词 embed → 余弦 topK → 回表过滤 → 按分排序。

| 文件 | 说明 |
|---|---|
| `sql/library_pgvector_schema.sql` | 新表 `library_file_embedding`（file_id 唯一；与知识库共用 knowledge_ai 库；服务启动时 ensureSchema 自动建表，也可手动执行） |
| `service/library/impl/LibraryVectorService` | pgvector 存取（复用 `knowledge.vector.datasource` 连接配置） |
| `LibraryService` / `Impl` | `semanticSearch(q, userId, limit)`、`backfillEmbeddings()`（每轮 ≤10 条，补历史文件）；`markAiDone` 后同步 embed；`purge` 时删向量 |
| `LibraryController` | `GET /library/files/semantic-search?q=&limit=20` |
| `LibraryFileVO` | 新增 `score`（0~1，仅语义检索返回） |
| `LibraryMaintenanceJob` | 新增 30s 间隔向量回填任务 |

**覆盖范围**：只有 AI 摘要成功（DONE）的文件有向量；语义检索按 user_id 隔离，不含回收站。

## 四、前端（AI-frontend 仓库）

- 新页 `pages/admin/OpsLogsStreamPage.vue`（`/admin/ops/logs`，终端风格：级别过滤/关键字过滤/暂停/清屏/复制/自动滚动）+ `api/opsLogStream.ts`（EventSource 封装，断线自愈）。
- 档案库搜索框 ≥2 字触发 500ms 防抖语义检索；命中文件按匹配分排序并显示「语义 N%」徽章（`LibraryPage.vue`、`library.css`）。

## 五、冒烟记录（2026-09-14 本地）

- SSE：回放启动日志 ✓、`ready` ✓、登录触发的 AUDIT 事件实时推送 ✓。
- 全闭环：注册临时 admin → upload-init（SHA-256 去重）→ 预签名 PUT MinIO 200 → upload-complete（PENDING）→ LLM 摘要 DONE → 向量落库 → `semantic-search?q=爱因斯坦纠缠实验拿了诺奖` 命中 quantum-notes.md ✓（查询词与原文零重叠，语义匹配生效）。
- 清理：测试文件 purge（MinIO 对象 + 向量同步删除 ✓）、临时账号与审计记录已删。
- 附带发现并修复：本机 `knowledge-ai-pg`（5432）与 `qa-minio` 同样依赖 Docker Desktop，停机时会拖垮 RAG 与知识库转档，已随 qa-minio 一并拉起。
