# AI 精读模块 · 评审落地进度清单（后端）

> **来源**：`AI_READING_DESIGN_REVIEW.md` 评审稿 → 后端落地
> **状态**：P0 全部完成；P1 全部完成后端实现；P2 除「弹性布局 / 装饰即状态 / 固定舞台」纯视觉项外已完成（2026-08-01）
> **图例**：✅ 已完成 ｜ 🔧 半完成（缺前端/运维接线） ｜ ⬜ 未开始

---

## P0 —— 已全部完成

| 项 | 落地内容 | 主要位置 |
| --- | --- | --- |
| 门闩置信度 / 可覆盖 / 判定缓存 / 错误回写 | `confidence` 字段、按 `domainId+question` 缓存 7 天、`overrideIntent+overrideReason` 带理由跳过（override pass 不受 LEAF 强校验）、`POST /gate/{gatePassId}/report-mistake` 回写并失效缓存 | `LearningTreeGateServiceImpl`、`LearningGatePassStore`、`LearningGateVO` |
| 句级溯源 + 冲突检测 | 材料包带 `sourceRef(srcN)`，合蒸稿段落标注 `[srcN]`，矛盾并列不裁决 | `KnowledgeDeepSeekReadingService`、`KnowledgeMergeDistillServiceImpl` |
| 压缩材料包 | reading 只输出要点+关键引文+结构化字段，目标 20%~30% | `materializeSystemPrompt` |
| 模型分层 | distill 默认 flash；pro 精修仅在发布博客/入库 RAG 前，pro→flash→原稿三级降级 | `KnowledgeDeepSeekProperties`、`KnowledgeQualityPolishService` |
| 复习状态机 + 掌握度 | `knowledge_note.review_status`(NEW/REVIEWING/MASTERED)、枝级 `masteredCount/masteryPercent`、`GET /branches/{id}/review-quiz`、note 复习状态/归档端点 | 迁移 `V16`、`KnowledgeNoteServiceImpl`、`LearningTreeServiceImpl` |

---

## P1 —— 已全部完成后端实现

| 项 | 落地内容 | 位置 / 端点 |
| --- | --- | --- |
| 失败任务差量重试 | 仅 FAILED 可重试、只重读 `failedSources`、上限 1 次（`retry_count` 落库）、排队满熔断 | `POST /admin/knowledge/reading-jobs/{jobId}/retry`（不重复校验门闩） |
| 大纲确认步 | submit 带 `outlineConfirm=true` → flash 大纲 → `AWAITING_OUTLINE`；确认后按大纲蒸馏 | `POST /reading-jobs/{jobId}/confirm-outline`（body `{outline}`） |
| 任务完成通知 | SSE 推送 job 状态（WAITING/SUCCESS/FAILED），轮询兜底 | `GET /reading-jobs/stream`（`ReadingJobNotifier`） |
| 差量返工 | reindex 按 chunk 内容 diff 只重 embedding 变化块；redistill 用户编辑过时带「参考草稿」只重蒸编辑部分 | `KnowledgeVectorStoreServiceImpl`（upsert/delete indexes）、`KnowledgeDistillationServiceImpl` |
| 审计日志接线 | 发布/同步/入库/重建/重蒸/挂叶/移叶/摘叶/合并枝/门闩全部留痕 | `OpsAuditActionConstant` 新 action |
| 版权合规 | 发布博客强制附原文链接（`publish.force_source_link` 开关，默认开，已含链接不重复） | `KnowledgeNotePublishServiceImpl.appendSourceLink` |
| 长上下文分块蒸馏 | 材料 >120k 字符按 `[srcN]` 分块各蒸 → 合并对齐大纲，保留溯源标注 | `KnowledgeMergeDistillServiceImpl.mergeDistillChunked` |
| 失败风暴 / 排队上限 | 单用户 PENDING+RUNNING 上限（`ingest.max_queued` 默认 5）；单 RUNNING 槽串行 | `assertQueueCapacity` |
| 验收脚本 G~J | 新增 17 个用例：retry 差量/回退/上限/熔断、门闩低置信/override/缓存命中、多 tab 最后保存、merge 审计 | `KnowledgeReadingJobServiceImplTest`、`LearningTreeGateServiceImplTest`、`KnowledgeNoteServiceImplTest` 等 |
| 余额预警熔断 | `ingest.balance_check_enabled` 开关（默认关）；查 DeepSeek `/user/balance`，<0.5 元拒绝新任务；检查失败 fail-open；结果缓存 10 分钟 | `DeepSeekBalanceGuard` |

**P1 连带**：SSRF 防护（协议白名单+私有网段+重定向≤5 跳）、材料包 URL 缓存 30 天、搜索候选「已在库」标注、QUEUED 超时、对齐度自检、job 粒度用量落库（`ai_usage_log.job_id`）、note 归档、标签管理、枝级综述、整库 zip 导出。

---

## P2 与遗留 —— 待做（多为前端 / 运维接线）

| 项 | 现状 | 需要做的 |
| --- | --- | --- |
| 断线恢复提示 | ✅ 前端采集页/学习页监听 `online` + `visibilitychange`，重连后重新挂接进行中 job（轮询兜底） | 已完成 |
| 多 tab 冲突提示 | ✅ 详情页保存前对比服务端 `updateTime`，提示「已有更新的保存」（覆盖 / 重新加载） | 已完成（乐观并发版本号可选，暂未做） |
| 材料缓存手动刷新 | ✅ `DELETE /admin/knowledge/learning/material-cache`（清 Redis+内存 `learning:material:*`）+ 采集页「强制刷新材料缓存」入口 | 已完成 |
| 用量看板月度视图 | ✅ `GET /admin/ops/usage/monthly?month=yyyy-MM`（按天序列 + byScene/byModel 含 token）+ 前端月度视图/按模型成本估算列；`pageLogs` 新增 `jobId` 过滤 | 已完成（单价为混合估算，非计费依据） |
| 单篇 distill 对齐度 | ✅ `KnowledgeAlignmentChecker` 共用组件，URL/文件单篇蒸馏与重蒸均写入 `alignmentScore/alignmentNote`（目标取 `agentQuery:` 标记，缺省用源标题） | 已完成 |
| 原文失效溯源降级 | ✅ 详情页来源链接 HEAD 探测，明确 404/410 时弹窗展示 `source_document.raw_text` 快照 | 已完成（CORS 拦截时按可打开兜底） |
| 弹性布局 / 装饰即状态 / 固定舞台 | ✅ 学习页按方案 A 落地：脱离固定舞台改为弹性三栏（220\|mid\|260）、右栏星图/叶密度绑定真实 leafCount、空枝虚线态、复习状态统计；`ReadingRoomShell` 支持弹性模式（当前仅 learning 路由，笔记/详情仍为舞台模式） | 已完成（其余页面待扩展弹性模式） |
| 复习 UI / 空枝补学 CTA / 键盘流 | ✅ 枝级复习弹窗（`review-quiz`，答案折叠）、叶复习状态 Tag（`LearningLeafVO.reviewStatus`）、键盘流 `/` 聚焦提问、`R` 复习、`?` 帮助；空枝补学 CTA 已就绪 | 已完成 |

**P2 备注（2026-08-01）**：
- 前端 `vue-tsc` 全量仍有存量报错（blog/diary/home 装饰组件等，非本次改动引入）；本次改动文件无新增类型错误，`vite build` 通过。
- 后端 `mvn compile` 与知识模块 4 个测试类（`KnowledgeReadingJobServiceImplTest`、`LearningTreeGateServiceImplTest`、`KnowledgeNoteServiceImplTest`、`LearningTreeServiceImplTest`）通过。
- 成本估算单价放在 `AI-frontend/src/utils/modelCost.ts`，按模型可调，标注为参考估算。

---

## 测试与迁移

- **测试**：`mvnw test` 全量 63 个用例通过（新增 17 个覆盖 G~J）。
- **迁移**：`V16__knowledge_reading_review.sql`（幂等：`knowledge_note` 加 review_status/is_archived/alignment_score/alignment_note；`knowledge_reading_job` 加 retry_count；`ai_usage_log` 加 job_id）。
- **新设置项**（`reading` 模块设置中心）：`publish.force_source_link`、`ingest.balance_check_enabled`、`ingest.max_queued`。

*后端 P0+P1 改动均向后兼容（新增字段/端点，未破坏既有接口）。*
