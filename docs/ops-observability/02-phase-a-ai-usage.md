# 02 Phase A：AI / Token 用量

> 依赖：site-settings P5（`ops.usage_log_*` 已存在）。  
> 对齐历史设想 [`../05-v5-extensions.md`](../05-v5-extensions.md) §6–7；**表与实现细节以本文为准**。  
> 前端对接：[FRONTEND_SYNC_A.md](./FRONTEND_SYNC_A.md)

## 1. 阶段目标

```text
ops.usage_log_enabled=true
  → AI 调用落库 ai_usage_log
  → Admin 可看今日聚合 + 明细
  → 按 usage_log_retain_days 清理
```

## 2. 数据表 `ai_usage_log`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| user_id | bigint | 可空（系统任务） |
| scene | varchar(64) | 见下表 |
| conversation_id | bigint | 可空；知识库会话 / Agent 会话等 |
| model_name | varchar(128) | |
| prompt_tokens | int | 可空（未知则 null） |
| completion_tokens | int | 可空 |
| total_tokens | int | 可空或求和 |
| response_time_ms | bigint | |
| status | varchar(32) | `success` / `error` |
| error_message | varchar(512) | 截断；无密钥 |
| request_summary | varchar(256) | 可选摘要，非全文 Prompt |
| create_time | datetime | |

索引建议：`(create_time)`、`(scene, create_time)`、`(user_id, create_time)`。

### scene 枚举（冻结）

| scene | 说明 |
| --- | --- |
| `knowledge_chat` | 知识库问答 |
| `distill` | 精读蒸馏 |
| `agent` | 角色 Agent |
| `codegen` | 应用代码生成 |
| `caption` | 聊天图片转述 |
| `segmentation` | 朗读分段 |
| `other` | 兜底 |

## 3. 埋点收口（后端）

优先在统一调用出口记录，避免每个 Controller 散落：

| 优先接入 | 位置（示意） |
| --- | --- |
| 知识库 chat/embed（可记 chat） | `KnowledgeAiModelServiceImpl` |
| 蒸馏 | `KnowledgeDistillationServiceImpl` → 经 AI 服务时可带 scene |
| Agent | `ChatAgentFacade` / 编排收尾 |
| Codegen | `AiCodeGeneratorFacade` 或 `AppServiceImpl.chatToGenCode` |
| Caption / Segmentation | 对应 Service 成功/失败路径 |

规则：

- 读取 `OpsRuntimeSettings.usageLogEnabled()`；false 则直接 return。
- 拿不到 token 时：仍记次数 + `response_time_ms` + status，tokens 置 null。
- 异步写入优先；失败只打应用日志，**不影响主链路**。

## 4. API（Admin）

```text
GET /api/admin/ops/usage/summary?from=&to=
GET /api/admin/ops/usage/logs?scene=&userId=&pageNum=&pageSize=
```

### summary 建议字段

- 请求次数、success/error 次数
- total_tokens（可空则仅展示有值部分）
- 平均 response_time_ms
- 按 scene / model 分组占比（简单 map 即可）

均需 `@AuthCheck` admin。

## 5. 清理 Job

- 定时（如每日）：删除 `create_time < now - retain_days`
- retain_days 来自 `ops.usage_log_retain_days`

## 6. 验收清单

- [ ] `usage_log_enabled=false` 时库表无新增。
- [ ] 开启后知识库问答或蒸馏至少有一条 success 记录。
- [ ] summary / logs 仅 admin；响应无 API Key。
- [ ] 清理 Job 按天数删除旧数据。

## 7. 明确不做（A）

- 计费账单、多币种成本折算
- 完整 Prompt/回答全文入库
- 向量库连接探测埋点（已有 health）
