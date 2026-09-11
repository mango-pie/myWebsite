# 全站设置中心 P5 — 前端对接说明（运维）

> 依赖 P0～P4。后端已实现配置审计、依赖健康探测与 `ops` 运维开关。  
> 产品说明见 [07-p5-ops.md](./07-p5-ops.md)。

## 1. 侧栏「运维」分组

与表单 Tab **分离**：

| 页面 | 路由建议 | 说明 |
| --- | --- | --- |
| 变更审计 | `/admin/settings/audit` | 列表分页 |
| 依赖健康 | `/admin/settings/health` | 卡片 + 重测 |
| 运维开关 | `/admin/settings/ops` | 普通设置表单（module=`ops`） |

业务模块侧栏以 `GET /modules` 为准；`ops` 会出现在 modules 列表（`phase=P5`）。

## 2. 审计 API

```http
GET /api/admin/site-settings/audit?module=&pageNum=1&pageSize=20
```

响应 `data` 为分页（MyBatis-Flex Page）：含 `records` / `totalRow` 等。

每条记录字段：

| 字段 | 说明 |
| --- | --- |
| `module` | 模块 |
| `settingKey` | 键 |
| `oldValue` / `newValue` | 已脱敏；敏感为 `***` |
| `operatorId` | 操作人 |
| `action` | `UPDATE` / `RESET` |
| `createTime` | 时间 |

仅 admin。`module` 可选筛选。

## 3. 健康 API

```http
GET  /api/admin/site-settings/health
POST /api/admin/site-settings/health/{target}
```

单测 target 支持：`redis` | `vector` | `knowledge_ai` | `agent` | `codegen` | `jina` | `astrbot` | `tts` | `minio`

响应项：`{ target, ok, latencyMs, message }` — **不含密钥 / JDBC URL**。

兼容：`POST /api/admin/site-settings/integration/test` body `{ "target": "..." }` 与单测同源。

UI：卡片列表展示全部健康结果；「重新检测」可调 GET health 或单条 POST。

## 4. ops 设置表单

沿用 P0 通用读写：

| 方法 | 路径 |
| --- | --- |
| GET/PUT/POST reset | `/api/admin/site-settings/ops` |

| key | UI |
| --- | --- |
| `debug_expose_error_detail` | 开关 + Confirm（danger） |
| `usage_log_enabled` | 开关（**已接线落库**；看板见 [`../ops-observability/FRONTEND_SYNC_A.md`](../ops-observability/FRONTEND_SYNC_A.md)） |
| `usage_log_retain_days` | 数字 |
| `ops_audit_enabled` | 开关（通用操作审计，见 [`FRONTEND_SYNC_B`](../ops-observability/FRONTEND_SYNC_B.md)） |
| `ops_audit_retain_days` | 数字（默认 90） |
| `biz_stats_enabled` | 开关（业务日统计，见 [`FRONTEND_SYNC_C`](../ops-observability/FRONTEND_SYNC_C.md)） |
| `http_log_enabled` | 开关（HTTP 日志，见 [`FRONTEND_SYNC_D`](../ops-observability/FRONTEND_SYNC_D.md)） |
| `http_log_mode` | 枚举；`all` 须 Confirm（danger） |
| `http_log_slow_ms` | 数字 |
| `http_log_retain_days` | 数字（默认 14） |

开启 `debug_expose_error_detail` 后，admin 看到的系统错误消息会附带更细原因（普通用户仍为「系统错误」）。

运维中心看板（用量 / 操作审计 / 业务统计 / 访问日志）：**不**挂在本设置表单下，见 [`../ops-observability/`](../ops-observability/README.md)。

## 5. 数据库

需执行 [`site_setting_audit_schema.sql`](../../src/main/resources/sql/site_setting_audit_schema.sql)。
