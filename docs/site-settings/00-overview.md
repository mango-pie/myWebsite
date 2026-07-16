# 00 全站设置中心总览

> 所属项目：Ai-Backend  
> 文档目录：[README.md](./README.md)

## 1. 定位

为管理员提供**全站级**系统设置能力，而不是某一个业务（如 RAG）的局部配置页。

```text
管理员进入「站点设置」
  ↓
按模块浏览 / 修改可运营参数与集成密钥
  ↓
后端校验 →（敏感字段加密）写入 DB → 刷新缓存
  ↓
各业务模块通过 SettingService 读取（DB 优先，YAML 回落）
```

## 2. 已确认产品决策

| 项 | 决策 |
| --- | --- |
| 文档与命名 | `docs/site-settings/`，中文分期文档 |
| 读者 | 前后端共用：接口契约 + 前端设置页信息架构 |
| P0 策略 | 先做平台骨架；**全模块 Key 清单在文档阶段定齐**，实现仍分期接入 |
| 用户偏好 | **本期不做** `/me/preferences`，仅 admin 系统设置 |
| 配置优先级 | **DB 覆盖 YAML**：有库用库，没有则回落 YAML / Properties 默认值 |
| AI Key / 路径 | **纳入** `integration` 模块（P1）；加密存储、接口脱敏 |
| 多模型 / Prompt | 行级多模型表、Prompt 模板库仍延后；单通道连接由 `integration` 覆盖 |
| 前端 | 每个阶段文档都写清页面 / 路由 / 交互要点 |

## 3. 设计原则

- **模块注册制**：新业务通过 `SettingModule` 注册 schema，而不是新开零散 `/xxx/config`。
- **横切与业务分离**：`site` / `security` / `upload` 先落地；集成密钥紧随其后；Chat、KB 等按阶段挂入。
- **敏感字段永不回显明文**：API Key 等加密入库，只返回 `configured` / 掩码；空值更新=不修改。
- **基础设施仍不下沉**：DB / Redis / 向量库连接、JWT 根密钥、加解密主密钥仍走环境变量。
- **改配置要可逆**：支持按模块 reset 到默认值（默认值来自 YAML/schema）。
- **前端由表单 schema 驱动**：避免每个模块硬编码整页表单。

## 4. 范围

### 4.1 本期包含

- Admin 全站设置中心 API：`/api/admin/site-settings/**`
- 模块：`site`、`security`、`upload`、`integration`、`chat`、`tts`、`knowledge`、`reading`、`blog`、`study`、`diary`、`app`
- AI / 外部服务 baseUrl、apiKey、业务路径的后台配置（`integration`）
- 设置管理前端：路由、侧栏模块导航、schema 驱动表单、保存 / 重置
- 可选：`GET /api/admin/site-settings/bootstrap` 供设置页初始化

### 4.2 本期明确不做

- 用户个人偏好 API / 页面
- 多模型行级 CRUD、Prompt 模板库（连接密钥已由 integration 覆盖）
- 任意在线修改 MySQL / Redis / 向量库连接
- 配置变更后的复杂工作流编排（如自动全量重建向量；最多提示副作用）

## 5. 架构示意

```text
前端 /admin/settings/:module
        │
        ▼
AdminSiteSettingsController
        │
        ▼
SiteSettingService  +  SettingModuleRegistry
        │
   DB(site_setting, 敏感字段密文) → Redis cache → YAML 默认值
        │
IntegrationCredentialsService / 各业务 Facade
```

## 6. 分期路线

| 阶段 | 文档 | 目标 |
| --- | --- | --- |
| P0 | [02-p0-platform.md](./02-p0-platform.md) | Registry、表、通用读写 API、site/security/upload、设置页壳 |
| P1 | [03-ai-integrations.md](./03-ai-integrations.md) | **integration：AI Key / baseUrl / 业务路径** |
| P2 | [04-p2-chat-tts.md](./04-p2-chat-tts.md) | 接入 chat / tts 行为参数 |
| P3 | [05-p3-knowledge-reading.md](./05-p3-knowledge-reading.md) | 接入 knowledge / reading |
| P4 | [06-p4-content-business.md](./06-p4-content-business.md) | 接入 blog / study / diary / app |
| P5 | [07-p5-ops.md](./07-p5-ops.md) | 审计日志、连通性探测、运维向开关 |

全模块 Key 契约见 [01-module-keys.md](./01-module-keys.md)。

## 7. 权限与安全

- 所有 `/api/admin/site-settings/**` 仅管理员（现有 `@AuthCheck` admin）。
- 响应脱敏；日志禁止打印密钥。
- 加解密主密钥 `SITE_SETTING_CRYPTO_SECRET` 仅环境变量。
- 高风险项（维护模式、路径变更、清空密钥 reset）前端二次确认；P5 补审计。

## 8. 验收总标准（全分期完成后）

- 管理员可在一个「站点设置」入口管理全站可运营参数与集成密钥。
- 修改后无需改 YAML / 重启即可对热更项生效（路径类可能需提示重启）。
- 未写入 DB 的 key 行为与当前 YAML 默认一致。
- 敏感配置不可被接口明文读出。
- 前端可根据 schema 渲染各模块表单；非法值有明确错误。
