# 02 P0：平台骨架与横切设置

> 目标：搭好全站设置中心的后端平台与前端壳，并接入 `site` / `security` / `upload`。  
> Key 契约见 [01-module-keys.md](./01-module-keys.md)。总览见 [00-overview.md](./00-overview.md)。

## 1. 阶段目标

```text
site_setting 表 + Registry + 通用 Admin API
  + site / security / upload 三个模块真实读写
  + 前端 /admin/settings 壳（侧栏 + schema 表单）
```

本阶段结束后：管理员能打开设置页，改站点名 / 注册开关 / 上传上限，并立即对业务生效（或下一次读取生效）。

## 2. 后端

### 2.1 数据库

建议表名：`site_setting`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint PK | 主键 |
| module | varchar(64) | 模块编码 |
| setting_key | varchar(128) | key（列名避免用 SQL 关键字 `key`） |
| setting_value | text | 序列化后的值 |
| value_type | varchar(32) | string/int/bool/... |
| sensitive | tinyint | 是否敏感 |
| updated_by | bigint | 最后修改人 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |
| is_delete | tinyint | 逻辑删除 |

唯一索引：`uk_module_key (module, setting_key, is_delete)`（或物理唯一 + 软删策略按项目惯例）。

SQL 建议落在：`src/main/resources/sql/site_setting_schema.sql`。

### 2.2 核心组件

| 组件 | 职责 |
| --- | --- |
| `SettingModule` | 模块接口：`code()` / `schema()` / `defaults()` / `validate()` / `onChanged()` |
| `SettingModuleRegistry` | 发现并索引全部模块 Bean |
| `SiteSettingService` | 读写 DB、脱敏、缓存、reset、聚合返回 |
| `SiteSettingCache` | Redis 或本地缓存；更新后失效 |
| `AdminSiteSettingsController` | 管理端 API |

读取伪逻辑：

```text
get(module, key):
  cache → DB → module.defaults() / YAML Properties → schema.default
```

业务侧封装建议：`SiteSettingService.getBool("security", "register_enabled", true)`，User 注册流程改为读此值。

### 2.3 API（约定前缀）

Context-path 已是 `/api`，Controller 映射建议：

```text
/admin/site-settings
```

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/modules` | 返回已注册模块列表（code、名称、阶段、是否可写） |
| GET | `/{module}/schema` | 表单 schema（字段、类型、范围、说明、sensitive） |
| GET | `/{module}` | 当前值（敏感字段脱敏） |
| PUT | `/{module}` | 批量更新：`{ "items": { "register_enabled": true, ... } }` |
| POST | `/{module}/reset` | 删除该模块 DB 覆盖，回落默认值 |
| GET | `/bootstrap` | 设置页初始化：模块列表 + 当前用户是否 admin |

权限：全部 `@AuthCheck(mustRole = admin)`。

#### 响应约定（示意）

`GET /{module}`：

```json
{
  "module": "security",
  "values": {
    "register_enabled": true,
    "default_user_role": "user"
  },
  "meta": {
    "register_enabled": { "source": "default", "sensitive": false },
    "default_user_role": { "source": "db", "sensitive": false }
  }
}
```

`source`：`db` | `default`（YAML/schema）。

### 2.4 P0 必接业务点

| 模块 | 必须接到真实代码 |
| --- | --- |
| `security.register_enabled` | 注册接口拒绝/允许 |
| `upload.max_file_size_mb` | 上传校验使用设置值 |
| `site.maintenance_mode` | 至少定义行为：非 admin 写接口返回维护中（可先做拦截器骨架） |
| `site.name` | `/bootstrap` 或设置页标题可用 |

`upload.allowed_image_ext`、`site.slogan` 等有接口即可；业务接线可本阶段一并完成。

### 2.5 缓存与生效

- 更新 / reset 后清除 `site_setting:{module}` 缓存。
- P0 不要求推送各 JVM 集群广播；单实例或同源 Redis 即可。
- 文档中标明：部分 Spring `@ConfigurationProperties` 绑定对象**不会自动热更新**——P0 要求业务读取走 `SiteSettingService`，而不是继续读已注入的静态 Properties 字段。

## 3. 前端

### 3.1 路由与入口

建议（按现有 admin 路由风格调整）：

```text
/admin/settings                 → 重定向到第一个模块，如 /admin/settings/site
/admin/settings/:module         → 模块设置页
```

侧栏入口：**站点设置**（仅 admin 可见）。

### 3.2 页面结构（一页多模块）

```text
┌──────────────────────────────────────────┐
│ 站点设置                                  │
├────────────┬─────────────────────────────┤
│ 站点       │  表单项（schema 驱动）        │
│ 安全       │  [保存] [恢复默认]           │
│ 上传       │  来源角标：默认 / 已自定义     │
│ （P1+ 灰显或隐藏未接入模块）              │
└────────────┴─────────────────────────────┘
```

P0 侧栏只启用：`site` / `security` / `upload`。  
下阶段优先接入：`integration`（AI Key / 路径，见 [03-ai-integrations.md](./03-ai-integrations.md)）。  
其余未接入模块：侧栏可展示但标记「即将接入」，点击提示或禁用。

### 3.3 交互要点

- 进入页面：`GET /bootstrap` → `GET /{module}/schema` + `GET /{module}`。
- 保存：`PUT /{module}`，成功 Toast；失败展示字段错误。
- 恢复默认：二次确认后 `POST /{module}/reset`。
- `maintenance_mode`、`register_enabled`：保存前 Confirm。
- 敏感字段：密码框或「已配置」；留空不提交该 key。

### 3.4 API 模块（前端）

建议 `src/api/siteSettings.ts`（路径按前端仓库调整）：

- `listModules()`
- `getSchema(module)`
- `getValues(module)`
- `updateValues(module, items)`
- `resetModule(module)`
- `bootstrap()`

表单组件：根据 `value_type` 映射 Input / Switch / InputNumber / TextArea。

## 4. 开发拆分

| 顺序 | 项 | 角色 |
| --- | --- | --- |
| 1 | SQL + Entity + Mapper | 后端 |
| 2 | Registry + Service + 三个 Module 实现 | 后端 |
| 3 | Controller + 注册/上传接线 | 后端 |
| 4 | 设置页壳 + 三模块表单 | 前端 |
| 5 | 联调验收 | 双方 |

## 5. 验收清单

### 后端

- [ ] 非 admin 访问设置 API 被拒绝。
- [ ] `PUT security` 关闭注册后，注册接口返回明确错误。
- [ ] 无 DB 记录时，返回值与 YAML/默认一致，`source=default`。
- [ ] 写入后再次 GET 为新值，`source=db`；reset 后回到 default。
- [ ] 敏感字段（若有）不明文返回。
- [ ] schema 覆盖 [01](./01-module-keys.md) 中 P0 三个模块全部 key。

### 前端

- [ ] admin 可从侧栏进入设置页。
- [ ] 三模块可切换、编辑、保存、恢复默认。
- [ ] 危险开关有确认。
- [ ] 未接入模块不导致路由白屏。

## 6. 明确不做（P0）

- chat / tts / knowledge 等真实接线（仅可在侧栏占位）。
- 用户偏好、多模型、Prompt CRUD。
- 配置审计表（P5）。
- AI Key / 路径接入（P1，见 [03-ai-integrations.md](./03-ai-integrations.md)）。
- 修改向量库 / MySQL / Redis 连接。
