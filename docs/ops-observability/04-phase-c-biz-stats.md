# 04 Phase C：业务日统计

> 建议在 A/B 之后。  
> 前端对接：[FRONTEND_SYNC_C.md](./FRONTEND_SYNC_C.md)

## 1. 阶段目标

```text
关键业务事件 → 写入/累加 biz_stat_daily
Admin overview 看近 N 天趋势
不做实时多维 OLAP
```

## 2. 数据表 `biz_stat_daily`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| stat_date | date | 业务日（按服务器时区或配置时区，文档实现时固定一种） |
| metric | varchar(64) | 见白名单 |
| dim | varchar(64) | 维度，默认 `_` 表示总计 |
| value | bigint | 累加值 |
| update_time | datetime | |

唯一键：`uk_date_metric_dim (stat_date, metric, dim)`。

写入：`INSERT ... ON DUPLICATE KEY UPDATE value = value + ?`（或等价原子累加）。

## 3. 首批 metric 白名单（冻结）

| metric | 说明 | 触发点（示意） |
| --- | --- | --- |
| `blog.post.view` | 博客阅读次数 | `incrementViewCount` 成功且 view 开关开启时 |
| `blog.post.like` | 点赞次数 | `incrementLikeCount` 成功 |
| `blog.post.publish` | 发布文章数 | 状态变为已发布 |
| `chat.conversation.create` | 新建聊天会话 | 会话创建 |
| `chat.message.user` | 用户消息条数 | 聊天落库用户侧 |
| `reading.ingest.success` | 精读采集成功 | ingest 成功 |
| `reading.distill.success` | 蒸馏成功 | distill 成功 |
| `study.habit.checkin` | 习惯打卡次数 | checkHabit |
| `study.focus.complete` | 专注完成次数 | 专注完成 |

新增 metric 必须先改本文。

## 4. 开关

- `ops.biz_stats_enabled`（默认 true）
- 关闭时累加器 no-op

## 5. API

```text
GET /api/admin/ops/stats/overview?from=&to=
GET /api/admin/ops/stats/series?metric=&from=&to=
```

`overview`：返回白名单各 metric 在区间内的合计 + 可选环比（首版可省略环比）。  
`series`：单 metric 按日序列，供折线图。

仅 admin。

## 6. 保留与清理

- 日表体积小：首版默认保留 **400 天**，可用常量或后续加 `biz_stats_retain_days`
- 清理 Job 删除过期 `stat_date`

## 7. 验收清单

- [ ] 阅读博客后 `blog.post.view` 当日 +1。
- [ ] overview 近 7 日有数据（联调账号）。
- [ ] `biz_stats_enabled=false` 后不再增长。

## 8. 明确不做（C）

- 实时大屏秒级刷新
- UV 精确去重（可用后期 HyperLogLog；首版只做次数 PV）
- 任意管理员自定义 SQL 报表
