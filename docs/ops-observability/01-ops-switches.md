# 01 ops 开关契约

> 全站设置模块 `ops` 已在 site-settings P5 落地部分 key。  
> 本文件冻结**运维可观测扩展 key**；实现前应同步回写  
> [`../site-settings/01-module-keys.md`](../site-settings/01-module-keys.md) § ops。

## 1. 模块

| 项 | 值 |
| --- | --- |
| module | `ops` |
| displayName | 运维开关 |
| phase | P5（设置壳）+ 本目录各 Phase 消费 |

读写仍走：

```text
GET/PUT/POST reset  /api/admin/site-settings/ops
```

## 2. Key 清单

### 2.1 已有（P5）

| key | type | default | 说明 | 消费阶段 |
| --- | --- | --- | --- | --- |
| `debug_expose_error_detail` | bool | `false` | admin 系统错误露详情 | 已接线 ExceptionHandler |
| `usage_log_enabled` | bool | `false` | AI 用量写入总开关 | **A**（落库实现后生效） |
| `usage_log_retain_days` | int | `30` | AI 用量保留天数 | **A** |

### 2.2 Phase B 新增

| key | type | default | 说明 |
| --- | --- | --- | --- |
| `ops_audit_enabled` | bool | `true` | 通用操作审计写入开关 |
| `ops_audit_retain_days` | int | `90` | 操作审计保留天数 |

### 2.3 Phase C 新增

| key | type | default | 说明 |
| --- | --- | --- | --- |
| `biz_stats_enabled` | bool | `true` | 业务日统计写入开关 |

> 业务统计通常不按「天数删明细」——日聚合表可用单独保留策略（如保留 400 天），首版可不单独 key，清理策略写在 Phase C 文档。

### 2.4 Phase D 新增

| key | type | default | 说明 |
| --- | --- | --- | --- |
| `http_log_enabled` | bool | `false` | HTTP 访问日志总开关 |
| `http_log_mode` | string | `errors_only` | 枚举见下 |
| `http_log_retain_days` | int | `14` | HTTP 日志保留天数 |
| `http_log_slow_ms` | int | `1000` | 慢请求阈值（ms），mode 含 slow 时生效 |

#### `http_log_mode` 枚举

| 值 | 行为 |
| --- | --- |
| `errors_only` | 仅 status ≥ 400 |
| `slow_and_errors` | ≥400 或 latency ≥ `http_log_slow_ms` |
| `all` | 全量（危险，schema `danger=true`，保存前 Confirm） |

## 3. Runtime 读取约定

实现时扩展 [`OpsRuntimeSettings`](../../src/main/java/com/ai/setting/runtime/OpsRuntimeSettings.java) 与 [`OpsModule`](../../src/main/java/com/ai/setting/module/OpsModule.java) schema；YAML 无独立 Properties，默认值写在 schema。

## 4. 变更流程

1. 先改本文件 + site-settings `01-module-keys.md`。
2. 再改 `OpsModule` / RuntimeSettings。
3. 各 Phase 业务代码只读 RuntimeSettings，禁止硬编码开关。
