# V5 扩展功能需求文档

## 1. 阶段目标

V5 是 Knowledge AI 的加分阶段，不建议在项目一开始就做。推荐先完成 V1 到 V4，保证基础后台、知识库管理、RAG 入库和 AI 聊天完整可用后，再逐步增加扩展功能。

V5 的目标是提升项目的工程完整度、后台配置能力和面试亮点，让项目更像一个可运营、可配置、可扩展的 AI 平台。

## 2. 扩展功能清单

V5 可以按优先级逐步实现：

1. Prompt 管理
2. 多模型配置
3. AI 参数配置
4. Token 统计
5. 调用日志
6. 知识库引用来源
7. MCP 工具扩展
8. Docker Compose 一键启动

## 3. Prompt 管理

### 功能目标

后台支持维护不同场景的 Prompt 模板，不需要每次修改代码才能调整 AI 回答风格。

### Prompt 类型

可以内置以下模板：

- 知识库问答助手
- Java 专家
- 翻译助手
- SQL 助手
- 代码助手
- 总结助手

### 功能需求

Prompt 管理包括：

- 新增 Prompt
- 编辑 Prompt
- 删除 Prompt
- 启用 / 禁用 Prompt
- 设置默认 Prompt
- 按场景选择 Prompt
- Prompt 变量说明

### Prompt 变量

建议支持以下变量：

- `{context}`：知识库上下文
- `{question}`：用户问题
- `{history}`：历史对话
- `{language}`：输出语言
- `{role}`：助手角色

### 数据库表 prompt_template

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| name | varchar | 模板名称 |
| code | varchar | 模板编码 |
| scene | varchar | 使用场景 |
| content | text | Prompt 内容 |
| enabled | tinyint | 是否启用 |
| default_flag | tinyint | 是否默认 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

## 4. 多模型配置

### 功能目标

系统支持配置多个模型供应商，后台可以切换当前使用的模型。

### 支持模型

建议支持：

- DeepSeek
- OpenAI
- Qwen
- Ollama

### 功能需求

多模型管理包括：

- 新增模型配置
- 编辑模型配置
- 删除模型配置
- 启用 / 禁用模型
- 设置默认聊天模型
- 设置默认 Embedding 模型
- 聊天时选择模型

### 模型配置字段

| 字段 | 说明 |
| --- | --- |
| provider | 模型供应商 |
| model_name | 模型名称 |
| api_key | API Key |
| base_url | Base URL |
| model_type | chat / embedding |
| enabled | 是否启用 |
| default_flag | 是否默认 |

### 安全要求

API Key 属于敏感信息，处理时需要注意：

- 数据库存储前建议加密
- 前端详情接口不返回完整 API Key
- 日志中不能打印 API Key
- 修改时可以重新覆盖 API Key

## 5. AI 参数配置

### 功能目标

后台支持动态调整模型参数，不需要修改配置文件或重新发布。

### 参数范围

支持配置：

- Temperature
- TopP
- Max Tokens
- TopK
- Presence Penalty
- Frequency Penalty
- 请求超时时间

### 使用场景

不同场景可以使用不同参数：

- 知识库问答：Temperature 较低，保证稳定性
- 创意写作：Temperature 较高，增强发散性
- 代码助手：Temperature 较低，减少随机性
- 翻译助手：Temperature 较低，保证准确性

## 6. Token 统计

> **实现文档以 [`ops-observability/`](./ops-observability/README.md) Phase A 为准**（表字段含 `scene`、Admin API、清理 Job、前端看板）。本节保留产品设想，细节若冲突以 ops-observability 为准。

### 功能目标

记录每次模型调用的 Token 使用情况、响应时间和调用结果，为后续成本分析和系统优化提供依据。

### 统计内容

需要记录：

- 请求用户
- 会话 ID
- 模型名称
- 输入 Token
- 输出 Token
- 总 Token
- 响应时间
- 调用状态
- 错误信息
- 调用时间

### 数据库表 ai_usage_log

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 用户 ID |
| conversation_id | bigint | 会话 ID |
| model_name | varchar | 模型名称 |
| prompt_tokens | int | 输入 Token |
| completion_tokens | int | 输出 Token |
| total_tokens | int | 总 Token |
| response_time_ms | bigint | 响应时间 |
| status | varchar | 调用状态 |
| error_message | text | 错误信息 |
| created_at | datetime | 创建时间 |

### 统计页面

后台可以展示：

- 今日请求次数
- 今日 Token 消耗
- 平均响应时间
- 调用成功率
- 用户调用排行
- 模型调用占比

## 7. 调用日志

> **AI 调用用量落库与运维看板**：见 [`ops-observability/02-phase-a-ai-usage.md`](./ops-observability/02-phase-a-ai-usage.md)。  
> 通用操作审计 / 业务统计 / HTTP 访问日志见同目录 Phase B～D。  
> 本节「链路细节」可与用量摘要并存；**勿存完整 Prompt 明文与 API Key**。

### 功能目标

记录 AI 调用链路，方便排查问题。

日志内容：

- 用户问题
- 检索到的 Chunk ID
- Prompt 摘要
- 模型名称
- 响应时间
- 调用状态
- 异常信息

注意：不要在日志中保存敏感 API Key。

## 8. 引用来源展示

### 功能目标

AI 回答后展示参考来源，让用户知道答案来自哪些文档片段。

展示内容：

- 文档名称
- 文档类型
- Chunk 片段摘要
- 相似度分数
- 点击查看原文

### 面试亮点

引用来源可以提升 RAG 系统可信度，减少“黑盒回答”的问题。用户不仅能看到答案，还能看到答案依据。

## 9. MCP 工具扩展

### 功能目标

在普通知识库问答之外，让 AI 可以调用外部工具，增强系统能力。

可扩展工具：

- 天气查询
- 数据库查询
- 网页搜索
- 邮件发送
- 日程创建
- 内部 API 查询

### 实现建议

MCP 属于高级扩展，不建议初期实现。可以在 V1 到 V4 稳定后，选择一个最简单的工具作为演示，例如天气查询或网页搜索。

## 10. Docker Compose 一键启动

### 功能目标

使用 Docker Compose 编排项目依赖，降低部署和演示成本。

建议编排：

- MySQL
- Redis
- PostgreSQL + pgvector
- MinIO
- 后端应用
- 前端 Nginx

### 面试亮点

可以说明项目不仅能本地开发，也考虑了部署和演示环境，通过 Docker Compose 管理基础设施依赖，方便快速启动整套系统。

## 11. V5 验收标准

- 后台可以维护 Prompt 模板
- 后台可以配置至少两个模型
- API Key 不会明文展示到前端
- 聊天时可以使用默认模型
- 系统可以记录 Token 使用情况
- 后台可以查看调用统计
- AI 回答可以展示引用来源
- Docker Compose 可以启动基础依赖

## 12. 开发优先级建议

V5 推荐顺序：

1. 先做引用来源展示，因为和 V4 关系最紧密，展示效果明显。
2. 再做 Prompt 管理，提升系统可配置性。
3. 然后做多模型配置，体现 AI 平台化能力。
4. 接着做 Token 统计和调用日志，体现工程完整度。
5. 最后尝试 MCP 工具扩展，作为高级亮点。

## 13. 面试讲法

V5 阶段可以这样讲：

在核心 RAG 和聊天能力完成后，我进一步扩展了 Prompt 管理、多模型配置、Token 统计和引用来源展示。Prompt 管理让系统可以不改代码调整 AI 行为，多模型配置让系统支持 DeepSeek、OpenAI、Qwen、Ollama 等不同供应商，Token 统计用于分析调用成本和响应性能，引用来源展示则提升了 RAG 问答的可信度。

如果面试官追问为什么 V5 不是一开始做，可以回答：

项目优先保证主链路完整，也就是登录认证、知识库管理、文档入库、向量检索和 AI 问答。等核心闭环跑通后，再做配置化和统计能力，这样开发风险更低，也更符合真实项目的迭代节奏。
