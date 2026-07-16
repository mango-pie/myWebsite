# 05 Phase D：HTTP 访问 / 错误日志

> 建议放在 A～C 之后。  
> 前端对接：[FRONTEND_SYNC_D.md](./FRONTEND_SYNC_D.md)

## 1. 阶段目标

```text
Filter/Interceptor 按 mode 采样写入 http_access_log
默认识记错误；全量需危险确认
不替代专业 ELK/APM
```

## 2. 数据表 `http_access_log`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| method | varchar(16) | |
| path | varchar(512) | 去 query 或截断 query |
| status | int | |
| latency_ms | bigint | |
| user_id | bigint | 可空 |
| ip | varchar(64) | |
| trace_id | varchar(64) | 请求内生成的简易 UUID |
| error_summary | varchar(512) | 4xx/5xx 时简短信息 |
| create_time | datetime | |

索引：`(create_time)`、`(status, create_time)`。

## 3. 开关与模式

见 [01-ops-switches.md](./01-ops-switches.md)：

- `http_log_enabled` 默认 false
- `http_log_mode`：`errors_only` | `slow_and_errors` | `all`
- `http_log_slow_ms` 默认 1000
- `http_log_retain_days` 默认 14

## 4. 实现要点

- Spring `OncePerRequestFilter` 或 HandlerInterceptor + afterCompletion
- 排除高频静态/健康噪音路径（如 `/favicon.ico`、actuator 若未来引入）
- **禁止**记录 Authorization 头、Cookie、请求体中的密钥
- 异步写库；失败不影响响应
- `trace_id` 可写入响应头 `X-Trace-Id`（可选，文档建议实现）

## 5. API

```text
GET /api/admin/ops/access-logs?status=&pathPrefix=&from=&to=&pageNum=&pageSize=
```

仅 admin。

## 6. 清理

按 `http_log_retain_days` 定时删除。

## 7. 验收清单

- [ ] enabled=false 无写入。
- [ ] errors_only：人为 404/业务错误有记录，正常 200 大量接口不刷屏。
- [ ] slow_and_errors：人为 sleep 或慢接口超过阈值可记。
- [ ] 响应与库中无 Authorization / apiKey。

## 8. 明确不做（D）

- 全量 body 抓包
- 分布式 tracing backend（Jaeger 等）集成（可后续）
- 替代 Nginx access log
