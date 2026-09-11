# 03 P1：AI 连接、密钥与路径

> 依赖 P0 平台可用。Key 见 [01-module-keys.md](./01-module-keys.md) 的 `integration`。  
> 本阶段解决「后台可配置各 AI / 外部服务的 baseUrl、apiKey，以及业务相关路径」，不再只能改 YAML / 环境变量。

## 1. 阶段目标

```text
管理员在「集成与密钥」页配置：
  - 各 AI / 外部服务的 baseUrl、apiKey
  - 上传目录、TTS 参考音频目录、部署目录等业务路径
  ↓
敏感值加密入库、接口脱敏
  ↓
业务通过 SettingService 读取（DB 覆盖 YAML）
```

推荐在 **P2（chat/tts）与 P3（knowledge）之前完成**，否则密钥仍只能靠环境变量联调。

## 2. 范围

### 2.1 包含

| 分组 | 内容 |
| --- | --- |
| 知识库 AI | `base_url`、`api_key` |
| Jina 抓取 | `base_url`（若后续有 key 也可加） |
| 聊天 / Agent / 图片转述 | DashScope 兼容 `base_url`、`api_key`；Caption 专用 key |
| 代码生成 | codegen `base_url`、`api_key` |
| AstrBot | `base_url`、`api_key` |
| GPT-SoVITS | `base_url`；参考音频目录、seed 音频路径 |
| 上传与部署路径 | `upload.path`、`upload.base_url`；`app.deploy` 目录与 host |

### 2.2 仍不包含

| 类别 | 原因 |
| --- | --- |
| MySQL / Redis / 向量库账号密码 | 基础设施，改错会导致整站不可用；继续环境变量 |
| JWT / Session 密钥 | 安全根密钥，禁止后台热改 |
| 多模型 CRUD 表、Prompt 模板库 | 仍延后（见 V5）；本阶段是「每通道一组 endpoint+key」 |

> MinIO：默认**可选接入**。若联调知识库强依赖后台改 MinIO，可将 endpoint / accessKey / secretKey / bucket 放进 `integration`（敏感字段加密）；否则保持环境变量，只在健康检查里探测。

## 3. 后端

### 3.1 模块

- `IntegrationSettingModule`（code = `integration`）

存储仍用 `site_setting`；对 `sensitive=true` 的 value：

1. 写入前用对称加密（如 AES-GCM）。
2. 主密钥来自环境变量，例如 `SITE_SETTING_CRYPTO_SECRET`（**本身不进 DB**）。
3. 读出给业务时在服务端解密；返回给前端时只给：
   - `configured: true/false`
   - 可选掩码：`****abcd`（后 4 位）
4. PUT 时该 key 传空串 / `null` → **不修改**已有密钥。

### 3.2 路径字段校验

- 禁止 `..` 路径穿越。
- 相对路径相对于应用工作目录解析；绝对路径允许但 schema 标 `danger`。
- 变更 `upload.path` / 部署目录后：`onChanged` 提示可能需重启静态资源映射或清缓存（实现时按现有 `UploadConfig` / `WebMvcConfig` 能力决定是否支持热更）。

### 3.3 业务接线

业务客户端创建或每次调用前读取：

| 消费方 | 读取的 integration key（见 01） |
| --- | --- |
| Knowledge AI 客户端 | `knowledge.ai.base_url` / `api_key` |
| Jina 抓取 | `knowledge.jina.base_url` |
| Tavily 搜索 | `knowledge.tavily.base_url` / `api_key` |
| DeepSeek 联网搜索 | `knowledge.deepseek.base_url` / `api_key` |
| Image caption | `chat.image_caption.base_url` / `api_key` |
| Agent chat model | `chat.agent.base_url` / `api_key` |
| Codegen 流式模型 | `codegen.base_url` / `api_key` |
| AstrBot | `astrbot.base_url` / `api_key` |
| GPT-SoVITS | `tts.base_url`、路径类 key |
| 上传 / 部署 | `upload.*`、`app.deploy.*` |

封装建议：`IntegrationCredentialsService`，内部调 `SiteSettingService`，避免各处重复解密逻辑。

### 3.4 API

通用接口：

```text
GET/PUT  /admin/site-settings/integration
GET      /admin/site-settings/integration/schema
POST     /admin/site-settings/integration/reset
```

可选增强（本阶段建议做，方便验收）：

```text
POST /admin/site-settings/integration/test
Body: { "target": "knowledge_ai" | "astrbot" | "tts" | "jina" | "codegen" | ... }
```

用**当前已保存**（或请求里临时填的）配置打只读探测，返回 `ok` / `latencyMs` / `message`，不回显密钥。  
完整健康面板可留到 P5，但至少提供 1～2 个常用 target 的 test。

### 3.5 与 YAML 关系

- 未在 DB 配置时：回落 `application.yml` / 环境变量（与现网一致）。
- DB 有值：覆盖 YAML。
- reset：删除 DB 覆盖，回到 YAML。

## 4. 前端

### 4.1 路由与侧栏

```text
/admin/settings/integration
```

侧栏名称建议：**集成与密钥**（放在「上传」之后、「角色聊天」之前）。

### 4.2 页面分组

```text
知识库 AI
Jina
聊天 / Agent / 图片转述
代码生成
AstrBot
语音 GPT-SoVITS
路径（上传 / TTS 参考音频 / 部署）
（可选）MinIO
```

### 4.3 交互要点

- 密钥类：Password 输入框；已配置显示「已配置 · 后四位 abcd」；占位符「不修改请留空」。
- URL / 路径：普通 Input；路径类保存前 Confirm。
- 每组可提供「测试连接」按钮 → `POST .../test`。
- 恢复默认：二次确认（会清除 DB 中的密钥覆盖）。

## 5. 开发拆分

| 顺序 | 项 |
| --- | --- |
| 1 | 敏感字段加解密工具 + 主密钥环境变量 |
| 2 | `IntegrationSettingModule` schema / validate |
| 3 | 各 AI 客户端改为读 IntegrationCredentialsService |
| 4 | test 接口 |
| 5 | 前端「集成与密钥」页 |

## 6. 验收清单

### 后端

- [ ] `integration` schema 覆盖 01 清单本模块全部 key。
- [ ] GET 不明文返回 apiKey；空 PUT 不覆盖旧密钥。
- [ ] DB 配置的 baseUrl/apiKey 实际被 Knowledge / Chat / TTS / AstrBot 等使用。
- [ ] reset 后回落环境变量 / YAML。
- [ ] 路径含 `..` 时校验失败。
- [ ] 日志与异常信息无完整密钥。

### 前端

- [ ] 分组表单可编辑、保存、重置。
- [ ] 密钥留空不覆盖；已配置状态可见。
- [ ] 至少一个「测试连接」可用。

## 7. 明确不做（本阶段）

- 多模型多行 CRUD（一家供应商多模型切换器）——仍见 V5。
- Prompt 模板库。
- 修改 MySQL / Redis / 向量库连接串。
- 用户级个人 API Key。
