# 07 P5：运维增强（审计 / 探测 / 可观测）

> 依赖 P0～P4 主体可用。本阶段增强运营与排障，不扩张新业务模块。  
> P1 已提供部分 `integration/test`；本阶段做成完整健康面板与审计。

## 1. 阶段目标

```text
配置变更可追溯
  + 关键外部依赖可「测连通」（只读探测）
  + 少量运维开关可后台控制
```

## 2. 后端

### 2.1 配置审计

建议表：`site_setting_audit`

| 字段 | 说明 |
| --- | --- |
| id | 主键 |
| module | 模块 |
| setting_key | 变更的 key；批量更新可多行或 JSON diff |
| old_value | 脱敏后旧值 |
| new_value | 脱敏后新值 |
| operator_id | 操作人 |
| action | UPDATE / RESET |
| create_time | 时间 |

API：

```text
GET /admin/site-settings/audit?module=&pageNum=&pageSize=
```

规则：

- 敏感字段审计只记 `***` / `changed=true`。
- reset 记模块级一条或逐 key。

### 2.2 连通性探测（只读）

**不提供**改 MySQL/Redis/向量库连接串；对「当前生效配置」（DB `integration` 覆盖或 YAML 回落）做探测：

| 探测项 | 行为 |
| --- | --- |
| Redis | PING |
| MinIO | bucket exists / list 权限轻量检查 |
| 向量库 Postgres | `SELECT 1` |
| AstrBot | health 或轻量 API |
| GPT-SoVITS | health（若有） |
| Knowledge AI Endpoint | models 或 embedding 小探测（注意成本） |

API 建议：

```text
GET  /admin/site-settings/health
POST /admin/site-settings/health/{target}
```

返回：`ok` / `message` / `latencyMs`，**禁止**回显密钥。

### 2.3 运维向设置（可选小组）

若需要独立 module，可用 `ops`（需回写 [01-module-keys.md](./01-module-keys.md)）：

| key | 说明 |
| --- | --- |
| `debug_expose_error_detail` | 是否对 admin 暴露更细错误 |
| `usage_log_enabled` | 预留：Token/调用日志总开关（**真正落库与看板见** [`../ops-observability/`](../ops-observability/README.md) Phase A） |
| `usage_log_retain_days` | 预留保留天数 |

P5 可将 `ops` 标为可选；不做也不阻塞验收其他项。

## 3. 前端

### 3.1 新增页面

```text
/admin/settings/audit          变更记录列表
/admin/settings/health         依赖健康检查
```

侧栏「运维」分组：

- 变更审计
- 依赖健康

### 3.2 交互

- 审计：按模块筛选、分页；展示操作者、时间、diff。
- 健康：卡片列表；按钮「重新检测」；失败显示简短原因。
- 与业务设置 Tab 分离，避免和表单页混在一起。

## 4. 验收清单

- [ ] 任意模块 PUT / reset 产生审计记录（敏感脱敏）。
- [ ] 非 admin 无法访问 audit / health。
- [ ] health 探测不返回密钥、连接串完整内容。
- [ ] 前端可查看审计与一键重测。

## 5. 明确不做（P5）

- 在线修改 MySQL / Redis / 向量库连接。
- 完整 Token 统计大盘与操作/业务/HTTP 日志（见 [`../ops-observability/`](../ops-observability/README.md)，A→B→C→D 分期实现）。
- 多模型行级 CRUD / Prompt 模板库（AI Key/URL 已在 P1）。
- 用户偏好。

## 6. 后续候选（本文档之外）

| 选题 | 说明 |
| --- | --- |
| 运维可观测 | [`../ops-observability/`](../ops-observability/README.md)：用量落库、操作审计、业务统计、HTTP 日志 |
| 多模型配置 | 专项表 + `/admin/site-settings/ai/models`（一家多模型切换） |
| Prompt 中心 | 专项表 + scene 绑定 |
| `/api/site/bootstrap` 对普通前端 | 仅非敏感开关（若将来需要） |
| 用户偏好 | `/api/me/preferences` |
