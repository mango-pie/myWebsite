# Phase C — 前端对接说明（业务日统计）

> 后端 **已实现**。规划见 [04-phase-c-biz-stats.md](./04-phase-c-biz-stats.md)。

## 1. 信息架构

| 区域 | 路由建议 | 说明 |
| --- | --- | --- |
| 业务统计大盘 | `/admin/ops/stats` | overview 卡片 + 折线 |
| 运维开关 | `/admin/settings/ops` | `biz_stats_enabled`（默认 true） |

## 2. API

Base：`/api`。日期 `yyyy-MM-dd`，缺省近 7 日。仅 admin。

### 2.1 概览

```http
GET /api/admin/ops/stats/overview?from=2026-07-01&to=2026-07-15
```

响应示例：

```json
{
  "code": 0,
  "data": {
    "totals": {
      "blog.post.view": 128,
      "blog.post.like": 12,
      "blog.post.publish": 3,
      "chat.conversation.create": 20,
      "chat.message.user": 85,
      "reading.ingest.success": 6,
      "reading.distill.success": 4,
      "study.habit.checkin": 9,
      "study.focus.complete": 5
    }
  },
  "message": "ok"
}
```

白名单每个 key 必有，无数据为 `0`。文案映射：

| metric | 文案 |
| --- | --- |
| `blog.post.view` | 博客阅读 |
| `blog.post.like` | 博客点赞 |
| `blog.post.publish` | 博客发布 |
| `chat.conversation.create` | 新建会话 |
| `chat.message.user` | 用户消息 |
| `reading.ingest.success` | 精读采集成功 |
| `reading.distill.success` | 蒸馏成功 |
| `study.habit.checkin` | 习惯打卡 |
| `study.focus.complete` | 专注完成 |

### 2.2 序列（折线）

```http
GET /api/admin/ops/stats/series?metric=blog.post.view&from=&to=
```

响应示例：

```json
{
  "code": 0,
  "data": [
    { "date": "2026-07-09", "value": 10 },
    { "date": "2026-07-10", "value": 0 },
    { "date": "2026-07-11", "value": 15 }
  ],
  "message": "ok"
}
```

**约定**：区间内按日补齐，无数据日 `value=0`，点数 = 天数。非法 metric → 业务错误（非 0 code）。

## 3. 交互

- 默认近 7 / 30 日快捷选择。
- `biz_stats_enabled=false` 时顶部提示并链到 `/admin/settings/ops`。
- 点击卡片切换 series 的 metric。

## 4. 联调清单

- [ ] 阅读博客后 `blog.post.view` 增长。
- [ ] series 点数与区间天数一致（含 0）。
- [ ] 关闭 `biz_stats_enabled` 后不再增长。

## 5. 数据库

[`biz_stat_daily_schema.sql`](../../src/main/resources/sql/biz_stat_daily_schema.sql)
