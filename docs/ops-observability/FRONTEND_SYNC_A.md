# Phase A — 前端对接说明（AI 用量）

> 后端 **已实现**。规划见 [02-phase-a-ai-usage.md](./02-phase-a-ai-usage.md)。  
> 开关在设置中心 `ops`；**看板在运维中心**，勿与 schema 表单混页。

## 1. 信息架构

| 区域 | 路由建议 | 说明 |
| --- | --- | --- |
| 运维开关 | `/admin/settings/ops` | `usage_log_enabled` / `usage_log_retain_days`（已生效） |
| AI 用量看板 | `/admin/ops/usage` | 聚合卡片 + 明细表 |

侧栏建议新增分组「运维中心」：`用量` / `操作审计` / …  
与「站点设置 → 运维」分组分离。

## 2. 前置：开启记录

1. `GET /api/admin/site-settings/ops` 确认 values。
2. `PUT /api/admin/site-settings/ops` 将 `usage_log_enabled` 设为 `true`。
3. 发起知识库问答或蒸馏后，再查用量 API。

关闭时后端 **零写入**（可不打点时也有少量判断）。

## 3. API

Base：`context-path=/api`，完整路径以 `/api` 开头。  
权限：均需 admin（`@AuthCheck`），非 admin → 403。

日期：`from` / `to` 为 `yyyy-MM-dd`；**缺省为近 7 日**（含当天）。

### 3.1 汇总

```http
GET /api/admin/ops/usage/summary?from=2026-07-01&to=2026-07-15
```

响应示例：

```json
{
  "code": 0,
  "data": {
    "requestCount": 12,
    "successCount": 11,
    "errorCount": 1,
    "totalTokens": 34567,
    "avgLatencyMs": 1823.5,
    "byScene": {
      "knowledge_chat": 8,
      "distill": 3,
      "agent": 1
    },
    "byModel": {
      "qwen-plus": 10,
      "unknown": 2
    }
  },
  "message": "ok"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| requestCount | number | 调用次数 |
| successCount / errorCount | number | 成功 / 失败 |
| totalTokens | number \| null | 有 token 的行合计；全无则为 null，UI 显示「—」 |
| avgLatencyMs | number \| null | 平均耗时 ms |
| byScene / byModel | object | scene/model → 次数 |

### 3.2 明细

```http
GET /api/admin/ops/usage/logs?scene=knowledge_chat&userId=&from=&to=&pageNum=1&pageSize=20
```

响应示例（MyBatis-Flex Page）：

```json
{
  "code": 0,
  "data": {
    "pageNumber": 1,
    "pageSize": 20,
    "totalRow": 12,
    "records": [
      {
        "id": 1,
        "userId": 1001,
        "scene": "knowledge_chat",
        "conversationId": 55,
        "modelName": "qwen-plus",
        "promptTokens": 1200,
        "completionTokens": 300,
        "totalTokens": 1500,
        "responseTimeMs": 2100,
        "status": "success",
        "errorMessage": null,
        "requestSummary": "什么是 RAG",
        "createTime": "2026-07-15T10:20:30"
      }
    ]
  },
  "message": "ok"
}
```

列建议：时间、scene、model、tokens、耗时、status、errorMessage（截断）。

`scene` 筛选枚举：

| scene | 文案 |
| --- | --- |
| knowledge_chat | 知识库问答 |
| distill | 精读蒸馏 |
| agent | Agent |
| codegen | 代码生成 |
| caption | 图片转述 |
| segmentation | 朗读分段 |
| other | 其他 |

**不返回** Prompt 全文 / API Key。

## 4. 交互

- 顶部：若 settings 中 `usage_log_enabled=false`，提示「当前未记录用量」，链到 `/admin/settings/ops`。
- 日期范围默认近 7 日。
- 分页 `pageSize` 最大 100。

## 5. 联调清单

- [ ] 开启开关后知识库问答 / 蒸馏，明细出现新行。
- [ ] 关闭开关后新请求不再增加。
- [ ] 非 admin 无法访问用量 API。
- [ ] summary 的 `byScene` 与明细筛选一致。

## 6. 数据库

部署执行：[`ai_usage_log_schema.sql`](../../src/main/resources/sql/ai_usage_log_schema.sql)
