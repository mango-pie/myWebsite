# 03 Phase B：通用操作审计

> 依赖：Phase A 非强制；可并行，但建议 A 后做。  
> 与 P5 [`site_setting_audit`](../../src/main/resources/sql/site_setting_audit_schema.sql) **并存**。  
> 前端对接：[FRONTEND_SYNC_B.md](./FRONTEND_SYNC_B.md)

## 1. 阶段目标

```text
登录与危险操作 → ops_audit_log
设置变更仍写 site_setting_audit
Admin「操作审计」可查运维动作
```

## 2. 与设置审计的关系

| 表 | 范围 |
| --- | --- |
| `site_setting_audit` | 仅 `PUT/reset /admin/site-settings/{module}` |
| `ops_audit_log` | 登录、部署、删资源、维护模式等高危业务操作 |

前端可：

- 分 Tab：「设置变更」调现有 `/admin/site-settings/audit`，「操作审计」调本文 API；或
- 运维中心两入口，不强制合并接口。

## 3. 数据表 `ops_audit_log`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | |
| operator_id | bigint | 可空（匿名登录失败） |
| action | varchar(64) | 见清单 |
| resource_type | varchar(64) | 如 `user` / `app` / `knowledge_base` / `site` |
| resource_id | varchar(64) | 可空 |
| ip | varchar(64) | 可空 |
| detail_json | text | 脱敏后 JSON，限长 |
| success | tinyint | 1/0 |
| create_time | datetime | |

索引：`(create_time)`、`(action, create_time)`、`(operator_id, create_time)`。

## 4. 首批 action 清单（冻结，实现逐项挂）

| action | 触发点（示意） |
| --- | --- |
| `user.login.success` | 登录成功 |
| `user.login.fail` | 登录失败（detail 不含密码） |
| `user.logout` | 登出 |
| `site.maintenance.toggle` | 维护模式变更（可与设置审计并存一条概要） |
| `app.deploy` | `AppServiceImpl.deployApp` |
| `app.delete` | 删除应用 |
| `knowledge.base.delete` | 删除知识库 |
| `knowledge.document.delete` | 删除文档 |
| `reading.note.delete` | 删除精读笔记（若有） |

后续新增 action 须先改本文再接线。

## 5. 写入规则

- 读 `ops.ops_audit_enabled`（新 key，见 [01-ops-switches.md](./01-ops-switches.md)）
- detail 禁止：密码、apiKey、token 明文
- 失败路径也可记（success=0），便于追撞库/撞密码

## 6. API

```text
GET /api/admin/ops/audit?action=&operatorId=&from=&to=&pageNum=&pageSize=
```

仅 admin。

## 7. 清理

按 `ops.ops_audit_retain_days`（默认 90）定时删除。

## 8. 验收清单

- [ ] 登录成功/失败各至少可查到记录（开启时）。
- [ ] app 部署产生 `app.deploy`。
- [ ] 关闭 `ops_audit_enabled` 后新操作不写表。
- [ ] 设置 PUT 仍只（或额外）出现在 `site_setting_audit`，不破坏 P5。

## 9. 明确不做（B）

- 替代码级 diff / Git 审计
- 全表数据变更 CDC
