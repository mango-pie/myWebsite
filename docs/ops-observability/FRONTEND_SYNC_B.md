# Phase B — 前端对接说明（操作审计）

> 后端 **已实现**。规划见 [03-phase-b-ops-audit.md](./03-phase-b-ops-audit.md)。  
> 设置变更审计仍用 P5 API；本页查**通用操作**。

## 1. 信息架构

| 区域 | 路由建议 | API |
| --- | --- | --- |
| 设置变更审计 | `/admin/settings/audit` | `GET /api/admin/site-settings/audit` |
| 操作审计 | `/admin/ops/audit` | `GET /api/admin/ops/audit` |
| 运维开关 | `/admin/settings/ops` | `ops_audit_enabled` / `ops_audit_retain_days` |

可选：运维中心「审计」页内 Tab：设置变更 | 操作审计（两套 API）。

## 2. 与 P5 设置审计对照

| | 设置变更 | 操作审计（本阶段） |
| --- | --- | --- |
| 表 | `site_setting_audit` | `ops_audit_log` |
| 范围 | PUT/reset 配置项 | 登录、部署、删资源、维护模式等 |
| 路径 | `/api/admin/site-settings/audit` | `/api/admin/ops/audit` |
| 开关 | 始终记（P5） | `ops_audit_enabled`（默认 true） |

## 3. API

```http
GET /api/admin/ops/audit?action=&operatorId=&from=&to=&pageNum=1&pageSize=20
```

- `from` / `to`：`yyyy-MM-dd`，缺省近 7 日
- 仅 admin

响应示例：

```json
{
  "code": 0,
  "data": {
    "pageNumber": 1,
    "pageSize": 20,
    "totalRow": 3,
    "records": [
      {
        "id": 1,
        "operatorId": 1001,
        "action": "user.login.success",
        "resourceType": "user",
        "resourceId": "1001",
        "ip": "127.0.0.1",
        "detailJson": "{\"userAccount\":\"admin\"}",
        "success": true,
        "createTime": "2026-07-15T11:00:00"
      },
      {
        "id": 2,
        "operatorId": null,
        "action": "user.login.fail",
        "resourceType": "user",
        "resourceId": null,
        "ip": "127.0.0.1",
        "detailJson": "{\"userAccount\":\"admin\",\"reason\":\"password_mismatch\"}",
        "success": false,
        "createTime": "2026-07-15T10:59:50"
      }
    ]
  },
  "message": "ok"
}
```

| 字段 | UI |
| --- | --- |
| createTime | 时间 |
| operatorId | 操作人（失败登录可空） |
| action | 动作码 → 中文 |
| resourceType / resourceId | 资源 |
| ip | IP |
| success | 成功/失败 Tag |
| detailJson | 折叠 JSON（已脱敏，无密码） |

### action 文案（首批）

| action | 文案 |
| --- | --- |
| `user.login.success` | 登录成功 |
| `user.login.fail` | 登录失败 |
| `user.logout` | 登出 |
| `site.maintenance.toggle` | 维护模式切换 |
| `app.deploy` | 应用部署 |
| `app.delete` | 删除应用 |
| `knowledge.base.delete` | 删除知识库 |
| `knowledge.document.delete` | 删除文档 |
| `reading.note.delete` | 删除精读笔记 |

## 4. ops 表单增量

后端 schema 已扩展，`GET /api/admin/site-settings/ops` / schema 驱动即可：

| key | UI | 默认 |
| --- | --- | --- |
| `ops_audit_enabled` | 开关 | true |
| `ops_audit_retain_days` | 数字 | 90 |

## 5. 联调清单

- [ ] 错误密码登录后可见 `user.login.fail`（detail 无密码）。
- [ ] 正确登录可见 `user.login.success`。
- [ ] 部署应用后可见 `app.deploy`。
- [ ] 设置 PUT 仍出现在 `/admin/settings/audit`；`maintenance_mode` 变更额外出现一条 `site.maintenance.toggle`。
- [ ] `ops_audit_enabled=false` 后新操作不写入本表。

## 6. 数据库

部署执行：[`ops_audit_log_schema.sql`](../../src/main/resources/sql/ops_audit_log_schema.sql)
