# Knowledge AI 文档目录 / Ai-Backend 文档入口

> **当前后端运行时约定**（与架构基线一致）：Java **17**、Maven、端口 **8123**、context **`/api`**、**Session 鉴权**（非 JWT）、业务模块开关 **`app.modules.*`**。  
> 新人请先读仓库根 [README.md](../README.md)，模块解耦权威文档：[module-decoupling/](./module-decoupling/README.md)，Schema 清单：[sql/README-modules.md](../src/main/resources/sql/README-modules.md)。  
> 下文 `00`～`06` 多为原 Knowledge AI **历史规划**；其中 JWT / 微服务等表述**不代表**本仓库现状。

这里存放 **原 Knowledge AI 独立项目** 的需求、阶段规划、简历材料和面试讲解文档（`00`～`06`），以及 Ai-Backend 集成后的联调说明。

**Ai-Backend 本仓库新增功能文档** 已单独目录存放，请勿混入本目录的 `00`～`06` 编号序列：

| 功能 | 入口 |
| --- | --- |
| AI 精读工作台 | [ai-reading-workbench/](./ai-reading-workbench/README.md) |
| 全站设置中心 | [site-settings/](./site-settings/README.md) |
| 运维可观测 | [ops-observability/](./ops-observability/README.md) |
| 模块解耦 | [module-decoupling/](./module-decoupling/README.md) |
| 模块设计文档 | [design/](#模块设计文档design自仓库根目录迁入) |
| 前端对接指南 | [frontend/](#前端对接指南frontend自仓库根目录迁入) |

## 文档列表（Knowledge AI 原项目规划）

- [00 项目需求总览](./00-project-overview.md)
- [01 V1 基础后台需求文档](./01-v1-basic-admin.md)
- [02 V2 知识库与文档管理需求文档](./02-v2-knowledge-document.md)
- [03 V3 RAG 核心需求文档](./03-v3-rag-core.md)
- [04 V4 AI 聊天需求文档](./04-v4-ai-chat.md)
- [05 V5 扩展功能需求文档](./05-v5-extensions.md)
- [06 简历与面试材料](./06-resume-and-interview.md)
- [后端集成说明（已并入 Ai-Backend）](./KNOWLEDGE_INTEGRATION.md)
- [知识库前端对接入口](./KNOWLEDGE_FRONTEND_SYNC.md)

## Ai-Backend 新功能：AI 精读工作台

| 文档 | 说明 |
| --- | --- |
| [工作台目录](./ai-reading-workbench/README.md) | 本功能文档入口 |
| [00 总览](./ai-reading-workbench/00-overview.md) | 产品目标与版本路线 |
| [01 V1 MVP](./ai-reading-workbench/01-v1-mvp.md) | 第一版范围与验收 |
| [02 V1 后端](./ai-reading-workbench/02-v1-backend.md) | 后端方案 |
| [03 V1 前端](./ai-reading-workbench/03-v1-frontend.md) | **给前端** |
| [04 V2](./ai-reading-workbench/04-v2-search-batch.md) | 搜索 API + 批量 |
| [05 V3](./ai-reading-workbench/05-v3-agent-path.md) | Agent 学习路线 |

## Ai-Backend 新功能：全站设置中心

| 文档 | 说明 |
| --- | --- |
| [设置中心目录](./site-settings/README.md) | 本功能文档入口 |
| [00 总览](./site-settings/00-overview.md) | 目标、原则、分期决策 |
| [01 模块 Key 清单](./site-settings/01-module-keys.md) | 全站 module/key 契约 |
| [02 P0 平台](./site-settings/02-p0-platform.md) | Registry、API、横切模块、设置页壳 |
| [P0 前端对接](./site-settings/FRONTEND_SYNC_P0.md) | 设置页接口与交互 |
| [03 P1 集成与密钥](./site-settings/03-ai-integrations.md) | AI Key / baseUrl / 业务路径 |
| [P1 前端对接](./site-settings/FRONTEND_SYNC_P1.md) | 集成与密钥页面 |
| [04 P2 聊天/TTS](./site-settings/04-p2-chat-tts.md) | chat / tts 行为参数 |
| [P2 前端对接](./site-settings/FRONTEND_SYNC_P2.md) | 聊天 / TTS 设置页 |
| [05 P3 知识库/精读](./site-settings/05-p3-knowledge-reading.md) | knowledge / reading |
| [P3 前端对接](./site-settings/FRONTEND_SYNC_P3.md) | 知识库 / 精读设置页 |
| [06 P4 内容业务](./site-settings/06-p4-content-business.md) | blog / study / diary / app |
| [P4 前端对接](./site-settings/FRONTEND_SYNC_P4.md) | 博客 / 学习 / 日记 / 应用设置页 |
| [07 P5 运维](./site-settings/07-p5-ops.md) | 审计、健康探测 |
| [P5 前端对接](./site-settings/FRONTEND_SYNC_P5.md) | 审计 / 健康 / ops |

## Ai-Backend 新功能：运维可观测

| 文档 | 说明 |
| --- | --- |
| [运维可观测目录](./ops-observability/README.md) | 本功能文档入口 |
| [00 总览](./ops-observability/00-overview.md) | 目标、原则、分期 A→B→C→D |
| [01 ops 开关](./ops-observability/01-ops-switches.md) | 扩展开关契约 |
| [Phase A～D](./ops-observability/02-phase-a-ai-usage.md) | AI 用量 → 操作审计 → 业务统计 → HTTP 日志 |
| [FRONTEND_SYNC_A～D](./ops-observability/FRONTEND_SYNC_A.md) | 运维中心前端对接 |

## 模块设计文档（design/，自仓库根目录迁入）

各业务域的架构 / 数据库设计稿，写实现前先读对应篇：

| 文档 | 说明 |
| --- | --- |
| [blog_database_design.md](./design/blog_database_design.md) | 博客模块数据库设计 |
| [blog_technical_implementation.md](./design/blog_technical_implementation.md) | 博客模块技术实现与 API 规范 |
| [chat-agent-ask-mode-design.md](./design/chat-agent-ask-mode-design.md) | 聊天 Ask / Agent 双模式架构设计 |
| [study_module_design.md](./design/study_module_design.md) | 学习模块（任务/清单/专注/习惯）设计 |
| [music-api-design.md](./design/music-api-design.md) | 音乐 API 设计（规划稿） |

## 前端对接指南（frontend/，自仓库根目录迁入）

面向前端的接口对接说明（SSE 协议、会话模型、TTS 等）：

| 文档 | 说明 |
| --- | --- |
| [chat-agent-frontend-guide.md](./frontend/chat-agent-frontend-guide.md) | Ask / Agent 双模式前端接入 |
| [chat-astrbot-frontend-guide.md](./frontend/chat-astrbot-frontend-guide.md) | AstrBot 角色聊天 SSE 前端迁移 |
| [chat-conversation-frontend-guide.md](./frontend/chat-conversation-frontend-guide.md) | 会话模型 v2 前端对接 |
| [chat-segmentation-frontend.md](./frontend/chat-segmentation-frontend.md) | 智能分段（segment_plan）前端处理 |
| [frontend-tts-api.md](./frontend/frontend-tts-api.md) | TTS 语音合成前端 API |

## 专项与交接文档（本目录根）

| 文档 | 说明 |
| --- | --- |
| [DEPLOY_SERVER.md](./DEPLOY_SERVER.md) | 服务器部署操作手册 |
| [DEPLOY_RECORD.md](./DEPLOY_RECORD.md) | 部署实录（2026-06-20） |
| [db-index-and-table-audit.md](./db-index-and-table-audit.md) | 数据库索引与表盘点（2026-09-12，V20 已落地） |
| [p0-tx-and-scheduler-refactor.md](./p0-tx-and-scheduler-refactor.md) | P0 事务边界与调度线程分离交接 |
| [p1-frontend-waiting-endpoints.md](./p1-frontend-waiting-endpoints.md) | 前端已在等待的三组缺失端点（v0.10.0 巡检） |

## 推荐阅读顺序

1. 了解原 Knowledge AI 背景：读 `00`～`04`（可选，历史规划）。
2. 了解当前已集成能力：`KNOWLEDGE_INTEGRATION.md` + `KNOWLEDGE_FRONTEND_SYNC.md`。
3. 开发精读工作台：进入 `ai-reading-workbench/`，按 `00` → `01` → `02`/`03` 阅读。
4. 开发全站设置：进入 `site-settings/`，按 `00` → `01` → `02` 阅读，再按阶段推进。
5. 开发日志与统计看板：进入 `ops-observability/`，按 A→B→C→D 推进。
6. 开发某个业务域（博客/学习/聊天 Agent 等）：先读 `design/` 对应设计稿，前端同学再配 `frontend/` 对接指南。
7. 准备简历面试时：读 `06-resume-and-interview.md`。

## 阶段重点（原 Knowledge AI）

| 阶段 | 文档 | 核心重点 |
| --- | --- | --- |
| 总览 | `00-project-overview.md` | 项目定位、技术栈、总体架构、开发路线 |
| V1 | `01-v1-basic-admin.md` | Spring Security、JWT、Redis、用户管理 |
| V2 | `02-v2-knowledge-document.md` | 知识库管理、文档上传、MinIO、MySQL 元数据 |
| V3 | `03-v3-rag-core.md` | 文档解析、文本切块、Embedding、pgvector |
| V4 | `04-v4-ai-chat.md` | RAG 问答、多轮对话、Prompt、SSE 流式输出 |
| V5 | `05-v5-extensions.md` | 多模型、Prompt 管理、Token 统计、MCP |
| 面试 | `06-resume-and-interview.md` | 简历描述、项目亮点、面试讲解主线 |
