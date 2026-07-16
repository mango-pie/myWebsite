# 全站设置中心（Site Settings）

本目录是 **Ai-Backend 本项目** 的全站系统设置规划与分期开发文档，与 `docs/00`～`06`（Knowledge AI 历史规划）、`docs/ai-reading-workbench/`（精读工作台）分开存放。

功能定位：为管理员提供统一的后台设置能力，覆盖站点安全、上传、**AI/外部服务密钥与路径**、聊天、TTS、知识库、精读、博客、学习、日记、应用生成等；配置以数据库为准、YAML 为回落默认值。

## 文档列表

| 文档 | 说明 | 读者 |
| --- | --- | --- |
| [00 总览](./00-overview.md) | 目标、原则、分期、已确认决策 | 全员 |
| [01 模块 Key 清单](./01-module-keys.md) | 全站 module / key 契约（实现可分期接入） | 后端 / 前端 |
| [02 P0 平台骨架](./02-p0-platform.md) | Registry、表结构、通用 API、横切模块、设置壳页面 | 后端 / 前端 |
| [FRONTEND_SYNC_P0](./FRONTEND_SYNC_P0.md) | **P0 前端对接**（接口与交互） | **前端优先** |
| [03 P1 集成与密钥](./03-ai-integrations.md) | AI baseUrl / apiKey / 业务路径 | 后端 / 前端 |
| [FRONTEND_SYNC_P1](./FRONTEND_SYNC_P1.md) | **P1 前端对接**（集成与密钥） | **前端优先** |
| [04 P2 聊天与 TTS](./04-p2-chat-tts.md) | `chat` / `tts` 行为参数 | 后端 / 前端 |
| [FRONTEND_SYNC_P2](./FRONTEND_SYNC_P2.md) | **P2 前端对接**（聊天 / TTS） | **前端优先** |
| [05 P3 知识库与精读](./05-p3-knowledge-reading.md) | `knowledge` / `reading` | 后端 / 前端 |
| [FRONTEND_SYNC_P3](./FRONTEND_SYNC_P3.md) | **P3 前端对接**（知识库 / 精读） | **前端优先** |
| [06 P4 内容与业务](./06-p4-content-business.md) | `blog` / `study` / `diary` / `app` | 后端 / 前端 |
| [FRONTEND_SYNC_P4](./FRONTEND_SYNC_P4.md) | **P4 前端对接**（内容业务） | **前端优先** |
| [07 P5 运维增强](./07-p5-ops.md) | 审计、连通性探测、可观测开关 | 后端 / 前端 |
| [FRONTEND_SYNC_P5](./FRONTEND_SYNC_P5.md) | **P5 前端对接**（审计 / 健康 / ops） | **前端优先** |

## 推荐阅读顺序

1. 先读 [00-overview.md](./00-overview.md)，对齐范围与分期。
2. 再读 [01-module-keys.md](./01-module-keys.md)，冻结首批 key 契约。
3. 做 P0：前后端都读 [02-p0-platform.md](./02-p0-platform.md)。
4. 做 P1 集成密钥：[03-ai-integrations.md](./03-ai-integrations.md)。
5. 再按 `04` → `05` → `06` 推进业务模块，稳定后看 `07`。

## 与现有文档关系

| 文档 / 模块 | 关系 |
| --- | --- |
| `docs/05-v5-extensions.md` | 多模型**行级** CRUD、Prompt 库仍可后续专项；**单通道 Key/URL 已由本目录 P1 覆盖** |
| `docs/ai-reading-workbench/` | 精读业务能力；连接密钥走 `integration`，行为参数走 `reading` |
| `docs/KNOWLEDGE_INTEGRATION.md` | 已集成知识库；`knowledge` + `integration` 分别管参数与连接 |
| 现有 `application.yml` / `*Properties` | 默认值与回落；热更项由 DB 覆盖 |

## 分期一览

```text
P0  平台骨架 + 横切（site / security / upload）+ 设置页壳
P1  integration：AI Key / baseUrl / 业务路径（加密 + 脱敏）
P2  chat + tts 行为参数
P3  knowledge + reading
P4  blog + study + diary + app
P5  审计 / 连通性 / 运维开关
```
