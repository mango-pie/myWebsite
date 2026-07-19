# Knowledge AI 文档目录

这里存放 **原 Knowledge AI 独立项目** 的需求、阶段规划、简历材料和面试讲解文档（`00`～`06`），以及 Ai-Backend 集成后的联调说明。

**Ai-Backend 本仓库新增功能文档** 已单独目录存放，请勿混入本目录的 `00`～`06` 编号序列：

| 功能 | 入口 |
| --- | --- |
| AI 精读工作台 | [ai-reading-workbench/](./ai-reading-workbench/README.md) |
| 全站设置中心 | [site-settings/](./site-settings/README.md) |
| 运维可观测 | [ops-observability/](./ops-observability/README.md) |
| 模块解耦 | [module-decoupling/](./module-decoupling/00-design-checklist.md) |

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

## 推荐阅读顺序

1. 了解原 Knowledge AI 背景：读 `00`～`04`（可选，历史规划）。
2. 了解当前已集成能力：`KNOWLEDGE_INTEGRATION.md` + `KNOWLEDGE_FRONTEND_SYNC.md`。
3. 开发精读工作台：进入 `ai-reading-workbench/`，按 `00` → `01` → `02`/`03` 阅读。
4. 开发全站设置：进入 `site-settings/`，按 `00` → `01` → `02` 阅读，再按阶段推进。
5. 开发日志与统计看板：进入 `ops-observability/`，按 A→B→C→D 推进。
6. 准备简历面试时：读 `06-resume-and-interview.md`。

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
