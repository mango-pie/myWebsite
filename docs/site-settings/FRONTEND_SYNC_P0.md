# 全站设置中心 P0 — 前端对接说明

> 后端已实现；前端在独立仓库按本文对接。  
> 产品与分期见 [00-overview.md](./00-overview.md)、[02-p0-platform.md](./02-p0-platform.md)。

## 1. 前提

- Context path：`/api`
- 全部接口需 **admin**（或 administrator）登录 Session
- 先在数据库执行：`src/main/resources/sql/site_setting_schema.sql`

## 2. 路由建议

```text
/admin/settings              → 重定向 /admin/settings/site
/admin/settings/:module      → 模块设置页
```

侧栏入口：**站点设置**（仅 admin）。P0 启用模块：`site` / `security` / `upload`。

## 3. API

Base：`/api/admin/site-settings`

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/bootstrap` | 初始化：admin 标记、站点名/标语、模块列表 |
| GET | `/modules` | 已注册模块列表 |
| GET | `/{module}/schema` | 表单 schema |
| GET | `/{module}` | 当前值 + meta（source / sensitive / configured） |
| PUT | `/{module}` | body: `{ "items": { "key": value } }` |
| POST | `/{module}/reset` | 清除该模块 DB 覆盖，回落默认 |

### 3.1 bootstrap 示例

```http
GET /api/admin/site-settings/bootstrap
```

```json
{
  "code": 0,
  "data": {
    "admin": true,
    "siteName": "Ai Scene",
    "siteSlogan": "",
    "modules": [
      { "code": "site", "displayName": "站点", "phase": "P0", "writable": true },
      { "code": "security", "displayName": "安全", "phase": "P0", "writable": true },
      { "code": "upload", "displayName": "上传", "phase": "P0", "writable": true }
    ]
  }
}
```

### 3.2 schema 示例

```http
GET /api/admin/site-settings/security/schema
```

字段含：`key`、`valueType`（string/int/bool…）、`label`、`description`、`defaultValue`、`sensitive`、`min`/`max`、`enumValues`、`danger`。

### 3.3 取值示例

```http
GET /api/admin/site-settings/security
```

```json
{
  "code": 0,
  "data": {
    "module": "security",
    "values": {
      "register_enabled": true,
      "default_user_role": "user",
      "login_fail_hint_generic": true
    },
    "meta": {
      "register_enabled": { "source": "default", "sensitive": false, "configured": false },
      "default_user_role": { "source": "db", "sensitive": false, "configured": true }
    }
  }
}
```

`source`：`default` = YAML/模块默认；`db` = 已自定义。

### 3.4 更新示例

```http
PUT /api/admin/site-settings/security
Content-Type: application/json

{
  "items": {
    "register_enabled": false
  }
}
```

敏感字段（P1 起）：传 `""` / `null` 表示不修改。P0 三模块无密钥字段。

### 3.5 重置

```http
POST /api/admin/site-settings/security/reset
```

## 4. P0 模块 Key

### site

| key | 类型 | 说明 |
| --- | --- | --- |
| name | string | 站点名称 |
| slogan | string | 标语 |
| maintenance_mode | bool | 维护模式（danger） |
| frontend_public_notice | string | 前端公告 |

### security

| key | 类型 | 说明 |
| --- | --- | --- |
| register_enabled | bool | 开放注册（danger） |
| default_user_role | string | 默认角色，仅 `user` |
| login_fail_hint_generic | bool | 登录失败模糊提示 |

### upload

| key | 类型 | 说明 |
| --- | --- | --- |
| max_file_size_mb | int | 1～50 |
| allowed_image_ext | string | 如 `jpg,jpeg,png,gif,webp` |
| presign_expire_seconds | int | 60～86400，知识库下载预签名已接线 |

## 5. 前端交互要点

1. 进入页：`bootstrap` → 当前 `module` 的 `schema` + 值。
2. 按 `valueType` 渲染：Switch / InputNumber / Input / TextArea。
3. `meta.source === 'db'` 可显示「已自定义」角标。
4. `danger === true`（维护模式、关闭注册）保存前 Confirm。
5. 恢复默认：Confirm 后调 `reset`。
6. 维护模式开启后：普通用户写接口返回 `code=40301`，前端可全局 Toast「系统维护中」。

## 6. 建议 API 封装

```ts
// siteSettings.ts
listModules()
bootstrap()
getSchema(module)
getValues(module)
updateValues(module, items)
resetModule(module)
```

## 7. 后续

P1「集成与密钥」会上 `integration` 模块（apiKey 脱敏、测试连接）。P0 侧栏可为未接入模块灰显占位。
