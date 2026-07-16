# 运维可观测（Ops Observability）

本目录是 **Ai-Backend** 的日志与统计规划文档：AI 用量、操作审计、业务日统计、HTTP 访问日志。  
与 [`../site-settings/`](../site-settings/README.md)（开关与保留天数）、[`../05-v5-extensions.md`](../05-v5-extensions.md)（历史 Token/日志设想）配套；**实现以本目录分期为准**。

功能定位：为管理员提供可排障、可算账、可看趋势的运维中心；**不**在线改基础设施连接。

## 文档列表

| 文档 | 说明 | 读者 |
| --- | --- | --- |
| [00 总览](./00-overview.md) | 目标、原则、分期、信息架构 | 全员 |
| [01 ops 开关契约](./01-ops-switches.md) | `ops` 模块扩展 key | 后端 / 前端 |
| [02 Phase A AI 用量](./02-phase-a-ai-usage.md) | `ai_usage_log` 埋点与看板 | 后端 / 前端 |
| [FRONTEND_SYNC_A](./FRONTEND_SYNC_A.md) | **A 前端对接** | **前端优先** |
| [03 Phase B 操作审计](./03-phase-b-ops-audit.md) | `ops_audit_log` + 与设置审计并存 | 后端 / 前端 |
| [FRONTEND_SYNC_B](./FRONTEND_SYNC_B.md) | **B 前端对接** | **前端优先** |
| [04 Phase C 业务统计](./04-phase-c-biz-stats.md) | `biz_stat_daily` 日聚合 | 后端 / 前端 |
| [FRONTEND_SYNC_C](./FRONTEND_SYNC_C.md) | **C 前端对接** | **前端优先** |
| [05 Phase D HTTP 日志](./05-phase-d-http-access.md) | `http_access_log` 采样访问日志 | 后端 / 前端 |
| [FRONTEND_SYNC_D](./FRONTEND_SYNC_D.md) | **D 前端对接** | **前端优先** |

## 推荐阅读顺序

1. [00-overview.md](./00-overview.md) 对齐范围与分期 A→B→C→D。
2. [01-ops-switches.md](./01-ops-switches.md) 冻结开关 key（并同步 site-settings `01-module-keys`）。
3. 实现时按 `02` → `03` → `04` → `05` 推进，每阶段对照对应 `FRONTEND_SYNC_*`。

## 与现有文档关系

| 文档 / 能力 | 关系 |
| --- | --- |
| [site-settings P5](../site-settings/07-p5-ops.md) | 已有设置变更审计、健康探测、`ops.usage_log_*` **预留开关** |
| [site-settings FRONTEND_SYNC_P5](../site-settings/FRONTEND_SYNC_P5.md) | 用量真正落库见本目录 Phase A |
| [05-v5-extensions Token/调用日志](../05-v5-extensions.md) | 产品设想；表字段与实现细节以本目录 Phase A 为准 |
| `site_setting_audit` | 仅设置 PUT/reset；通用操作审计见 Phase B |

## 分期一览

```text
A  AI/Token 用量日志 + 聚合看板     ✅ 已实现（联调见 FRONTEND_SYNC_A）
B  通用操作审计（登录 / 危险操作等） ✅ 已实现（联调见 FRONTEND_SYNC_B）
C  业务日统计大盘                   ✅ 已实现（联调见 FRONTEND_SYNC_C）
D  HTTP 访问 / 错误日志             ✅ 已实现（联调见 FRONTEND_SYNC_D）
```

部署前请执行 SQL：

- [`ai_usage_log_schema.sql`](../../src/main/resources/sql/ai_usage_log_schema.sql)
- [`ops_audit_log_schema.sql`](../../src/main/resources/sql/ops_audit_log_schema.sql)
- [`biz_stat_daily_schema.sql`](../../src/main/resources/sql/biz_stat_daily_schema.sql)
- [`http_access_log_schema.sql`](../../src/main/resources/sql/http_access_log_schema.sql)
