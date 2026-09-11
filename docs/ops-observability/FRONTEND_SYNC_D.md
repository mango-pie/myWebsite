# Phase D — 前端对接说明（HTTP 访问日志）

> 后端 **已实现**。规划见 [05-phase-d-http-access.md](./05-phase-d-http-access.md)。

## 1. 信息架构

| 区域 | 路由建议 | 说明 |
| --- | --- | --- |
| 访问日志 | `/admin/ops/access-logs` | 分页列表 |
| 运维开关 | `/admin/settings/ops` | http 相关 key |

## 2. API

```http
GET /api/admin/ops/access-logs?status=&statusClass=&pathPrefix=&from=&to=&pageNum=1&pageSize=20
```

| 参数 | 说明 |
| --- | --- |
| status | 精确状态码（如 404）；与 statusClass 二选一优先 status |
| statusClass | `4xx` / `5xx` |
| pathPrefix | 路径前缀，如 `/admin`（相对 context-path 后的 servlet path） |
| from / to | `yyyy-MM-dd`，缺省近 7 日 |

响应示例：

```json
{
  "code": 0,
  "data": {
    "pageNumber": 1,
    "pageSize": 20,
    "totalRow": 1,
    "records": [
      {
        "id": 1,
        "method": "GET",
        "path": "/admin/ops/usage/logs",
        "status": 404,
        "latencyMs": 12,
        "userId": 1001,
        "ip": "127.0.0.1",
        "traceId": "a1b2c3d4e5f6...",
        "errorSummary": "HTTP 404",
        "createTime": "2026-07-15T12:00:00"
      }
    ]
  },
  "message": "ok"
}
```

响应头：所有请求（含成功）可带 `X-Trace-Id`，便于对照日志。

**不返回** Authorization / Cookie / 请求体。

## 3. ops 表单增量

| key | UI | 默认 |
| --- | --- | --- |
| `http_log_enabled` | 开关 | false |
| `http_log_mode` | 枚举：`errors_only` / `slow_and_errors` / `all`（danger） | errors_only |
| `http_log_slow_ms` | 数字（slow 模式展示） | 1000 |
| `http_log_retain_days` | 数字 | 14 |

选择 `all` 时须 Confirm（磁盘与隐私风险）。

## 4. 交互

- 未开启时列表可为空 + 引导开开关。
- 默认模式只看 4xx/5xx，不会被正常 200 刷屏。

## 5. 联调清单

- [ ] `errors_only`：访问不存在路径有记录，大量 200 不出现。
- [ ] `slow_and_errors`：超阈值慢请求可记。
- [ ] `http_log_enabled=false` 后无新增。
- [ ] 响应头可见 `X-Trace-Id`。

## 6. 数据库

[`http_access_log_schema.sql`](../../src/main/resources/sql/http_access_log_schema.sql)
