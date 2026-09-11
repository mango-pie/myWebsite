# AI 智能知识库 Knowledge AI 项目需求总览

## 1. 项目定位

Knowledge AI 是一个面向简历展示和实际业务场景的 AI 智能知识库系统。系统支持用户上传企业文档、技术文档、制度文件或学习资料，并通过 RAG 检索增强生成能力，让用户可以围绕私有知识库进行自然语言问答。

项目重点体现以下能力：

- Java 21 + Spring Boot 3 后端工程能力
- Spring Security + JWT 认证授权能力
- Vue3 + TypeScript 前后端分离开发能力
- MinIO 文件存储与文档管理能力
- Spring AI 文档解析、Embedding、ChatModel 集成能力
- PostgreSQL + pgvector 向量检索能力
- SSE 流式响应与 AI 聊天交互能力
- Docker 本地部署和项目工程化能力

## 2. 项目目标

项目最终要实现一个完整的企业级知识库问答平台，用户可以创建知识库、上传文档、等待系统完成文档解析与向量化入库，然后通过聊天窗口针对知识库内容进行提问。

系统需要具备以下核心能力：

- 用户注册、登录、权限认证和用户管理
- 知识库创建、编辑、删除、查询
- 文档上传、删除、查看和存储
- PDF、DOCX、TXT、Markdown 文档解析
- 文本切块、Embedding 向量生成、pgvector 存储
- 基于用户问题进行向量相似度检索
- RAG Prompt 组装和大模型回答生成
- SSE 流式输出 AI 回复
- 多轮对话、历史消息和会话管理
- 多模型配置、Prompt 管理、Token 统计等扩展能力

## 3. 技术栈

### 后端

- Java 21
- Spring Boot 3
- Spring Security
- Spring AI
- MyBatis Plus
- MySQL
- Redis
- PostgreSQL
- pgvector
- MinIO
- Docker

### 前端

- Vue 3
- Vite
- TypeScript
- Pinia
- Vue Router
- Element Plus
- Axios
- Markdown 渲染
- SSE 流式接收

## 4. 总体架构

```text
Vue3 前端
  |
  | HTTP / SSE
  v
Spring Boot 后端
  |
  |-- Spring Security + JWT：登录认证、权限控制
  |-- MyBatis Plus + MySQL：业务数据存储
  |-- Redis：Token 缓存、验证码、热点数据
  |-- MinIO：文档与头像文件存储
  |-- Spring AI：文档解析、Embedding、ChatModel
  |-- PostgreSQL + pgvector：向量存储与相似度检索
```

## 5. 推荐项目结构

```text
knowledge-ai
├── knowledge-ai-admin      # Spring Boot 启动模块
├── knowledge-ai-common     # 通用响应、异常、工具类、基础配置
├── knowledge-ai-system     # 用户、角色、权限、登录日志
├── knowledge-ai-storage    # MinIO 文件存储模块
├── knowledge-ai-ai         # 大模型、Embedding、模型配置
├── knowledge-ai-rag        # 文档解析、文本切块、向量入库
├── knowledge-ai-chat       # 会话、消息、SSE 聊天
├── knowledge-ai-web        # Web Controller 接口层
└── knowledge-ai-ui         # Vue3 前端项目
```

## 6. 推荐开发阶段

| 阶段 | 名称 | 周期 | 目标 |
| --- | --- | --- | --- |
| V1 | 基础后台 | 约 1 周 | 完成登录注册、JWT 认证、用户管理、权限基础 |
| V2 | 知识库管理 | 约 1 周 | 完成知识库和文档管理，接入 MinIO 文件存储 |
| V3 | RAG 核心 | 约 2 周 | 完成文档解析、文本切块、Embedding、向量入库 |
| V4 | AI 聊天 | 约 2 周 | 完成知识库问答、多轮对话、SSE 流式输出 |
| V5 | 扩展能力 | 可选 | 完成多模型、Prompt、Token 统计、MCP 等加分项 |

## 7. 核心业务流程

### 文档入库流程

```text
用户上传文档
  |
  v
文件保存到 MinIO
  |
  v
MySQL 保存文档元数据
  |
  v
Spring AI 读取文档内容
  |
  v
TokenTextSplitter 文本切块
  |
  v
EmbeddingModel 生成向量
  |
  v
pgvector 保存 Chunk 和向量
  |
  v
文档状态更新为解析成功
```

### RAG 问答流程

```text
用户提问
  |
  v
问题 Embedding 向量化
  |
  v
pgvector 检索 TopK 相关 Chunk
  |
  v
组装 Prompt
  |
  v
调用大语言模型
  |
  v
SSE 流式返回回答
  |
  v
前端实时渲染 Markdown
```

## 8. 项目验收总标准

项目完成后应满足：

- 用户可以注册、登录、退出和修改密码
- 管理员可以管理用户、角色和基础权限
- 用户可以创建知识库并上传文档
- 文档文件可以保存到 MinIO
- 文档元数据可以保存到 MySQL
- PDF 等文档可以被解析成文本
- 文本可以切分为多个 Chunk
- Chunk 可以生成 Embedding 并保存到 pgvector
- 用户可以基于指定知识库进行 AI 问答
- AI 回复可以通过 SSE 流式展示
- 聊天历史可以保存和回看
- 项目可以通过 Docker 启动 MySQL、Redis、PostgreSQL、MinIO 等依赖

## 9. 简历完成版描述

AI 智能知识库系统是一个基于 Spring Boot 3、Spring AI 和 Vue3 构建的企业级知识库问答平台，支持文档上传、文本解析、文本切块、向量化存储、RAG 检索增强生成、多轮对话及 SSE 流式输出。系统采用 Spring Security + JWT 实现前后端分离认证，Redis 缓存登录态和热点数据，MySQL 存储业务数据，MinIO 存储文档文件，PostgreSQL + pgvector 实现语义向量检索，前端基于 Vue3、TypeScript 和 Element Plus 实现响应式交互。

项目通过 RAG 架构解决大模型无法直接访问私有知识的问题。用户上传文档后，系统自动完成文档解析、文本切块、Embedding 向量化和向量入库；用户提问时，系统先检索相关文档片段，再将上下文注入 Prompt 调用大模型生成回答，从而提升回答准确性和知识可控性。
