# 00 运维可观测总览

> 所属项目：Ai-Backend  
> 文档目录：[README.md](./README.md)

## 1. 定位

为管理员提供**可观测能力**：搞清楚「谁在何时调了什么 AI、做了什么敏感操作、业务量如何、接口是否报错」——而不是第二个 ELK/APM 产品。

```text
管理员进入「运维中心」
  ↓
用量 / 审计 / 业务统计 / 访问日志 Tab
  ↓
后端按 ops 开关决定是否写入
  ↓
定时任务按 retain_days 清理
```

## 2. 已确认决策

| 项 | 决策 |
| --- | --- |
| 文档目录 | `docs/ops-observability/` |
| 分期顺序 | **A → B → C → D**（AI 用量 → 操作审计 → 业务统计 → HTTP 日志） |
| 设置 vs 看板 | **设置中心**只放开关与保留天数；**看板**在 `/admin/ops/**` |
| 与设置审计 | `site_setting_audit` **保留**；通用操作另表 `ops_audit_log` 并存 |
| 敏感信息 | 禁止写入 API Key、完整 JDBC/Redis URL、超长 Prompt 明文 |
| 开关关闭 | 对应写入路径**零开销**（不查库、不拼大对象） |

## 3. 设计原则

- **收口埋点**：AI 调用尽量统一出口记录；业务统计用日聚合，避免扫明细表做大盘。
- **默认可控**：HTTP 默认只记错误；全量访问日志需显式开启并知悉风险。
- **可清理**：每类日志有独立保留天数；清理 Job 幂等。
- **前端分栏**：运维中心与「站点设置」表单分离，避免和 schema 驱动表单混页。

## 4. 范围

### 4.1 包含（分期实现）

| Phase | 能力 | 主表 |
| --- | --- | --- |
| A | AI Token/调用用量与明细 | `ai_usage_log` |
| B | 登录与危险操作审计 | `ops_audit_log` |
| C | 业务日指标 | `biz_stat_daily` |
| D | HTTP 访问/错误日志 | `http_access_log` |

运维开关继续挂在全站设置模块 `ops`（见 [01-ops-switches.md](./01-ops-switches.md)）。

### 4.2 明确不做

- 在线修改 MySQL / Redis / 向量库连接
- 完整分布式链路追踪 / 替代专业 APM
- 实时复杂 OLAP、多维下钻账单系统
- 用户个人偏好中心
- 多模型行级 CRUD / Prompt 模板库（见 site-settings / V5 延后项）

## 5. 前端信息架构（目标态）

```text
运维中心 /admin/ops
├── 依赖健康          → 复用 site-settings health（P5 已有）
├── 设置变更审计      → 复用 site-settings audit（P5 已有）
├── AI 用量与调用     → Phase A
├── 操作审计          → Phase B
├── 业务统计          → Phase C
└── 访问日志          → Phase D

站点设置 → ops Tab    → 各类开关与保留天数
```

## 6. 架构示意

```text
业务请求 / AI 调用
        │
        ├─(A)─► AiUsageLogService ──► ai_usage_log
        ├─(B)─► OpsAuditService   ──► ops_audit_log
        ├─(C)─► BizStatService    ──► biz_stat_daily
        └─(D)─► HttpAccessFilter  ──► http_access_log
                    ▲
                    │
            OpsRuntimeSettings（site_setting.ops）
```

## 7. 分期路线

| 阶段 | 文档 | 目标 |
| --- | --- | --- |
| A | [02-phase-a-ai-usage.md](./02-phase-a-ai-usage.md) | 打通 `usage_log_*`，落库 + 看板 |
| B | [03-phase-b-ops-audit.md](./03-phase-b-ops-audit.md) | 登录 / 危险操作可追溯 |
| C | [04-phase-c-biz-stats.md](./04-phase-c-biz-stats.md) | 日聚合业务大盘 |
| D | [05-phase-d-http-access.md](./05-phase-d-http-access.md) | 默认识记错误与慢请求 |
