# V4 AI 聊天需求文档

## 1. 阶段目标

V4 的目标是在 V3 向量入库能力之上，实现面向用户的 AI 知识库问答功能。

用户可以选择某个知识库进行提问，系统将用户问题向量化，从 pgvector 中检索相关文档片段，将检索结果作为上下文组装 Prompt，然后调用大语言模型生成回答，并通过 SSE 方式流式返回给前端。

这是项目最适合演示的阶段，也是简历中最能体现 AI 应用落地能力的部分。

## 2. 核心流程

```text
用户输入问题
  |
  v
保存用户消息
  |
  v
问题 Embedding 向量化
  |
  v
pgvector 检索 TopK 相关 Chunk
  |
  v
组装 RAG Prompt
  |
  v
调用 ChatModel / ChatClient
  |
  v
SSE 流式返回回答
  |
  v
前端实时展示 Markdown
  |
  v
保存 AI 完整回复
```

## 3. 会话管理需求

### 新建会话

用户可以基于某个知识库新建聊天会话。

会话字段：

- 会话标题
- 所属用户
- 关联知识库
- 创建时间
- 更新时间

会话标题可以采用以下策略：

- 默认使用用户第一条问题的前 20 个字符
- 或先显示“新会话”，第一轮问答后自动更新

### 会话列表

用户可以查看自己的历史会话。

列表展示：

- 会话标题
- 关联知识库名称
- 最近一条消息摘要
- 更新时间

### 删除会话

用户可以删除自己的会话。

删除规则：

- 删除会话时同步删除消息
- 可使用逻辑删除
- 用户只能删除自己的会话

## 4. 消息管理需求

系统需要保存用户消息和 AI 消息。

消息角色：

- `USER`：用户消息
- `ASSISTANT`：AI 回复
- `SYSTEM`：系统提示，可选

消息内容：

- 用户问题原文
- AI 完整回答
- Token 数量，可选
- 使用模型，可选
- 检索到的 Chunk 引用，可选

## 5. RAG 检索需求

### 用户提问处理

用户提交问题后，系统需要：

- 校验用户是否拥有知识库访问权限
- 保存用户消息
- 将问题转换为 Embedding 向量
- 在当前知识库范围内检索相似 Chunk
- 默认返回 Top5 结果
- 过滤相似度过低的结果

### TopK 检索策略

默认配置：

- `topK = 5`
- 相似度阈值可选
- 检索范围限定为当前知识库

后续可扩展：

- 支持用户选择多个知识库
- 支持混合检索：关键词 + 向量
- 支持重排序 Rerank

## 6. Prompt 组装需求

基础 Prompt 模板：

```text
你是一个企业知识库问答助手。
请只根据提供的知识库上下文回答用户问题。
如果上下文中没有答案，请回答“当前知识库中没有找到相关信息”。
不要编造知识库中不存在的内容。

知识库上下文：
{context}

历史对话：
{history}

用户问题：
{question}
```

### 上下文组装规则

上下文内容来自 TopK Chunk。

建议格式：

```text
[文档：xxx.pdf，片段 1]
chunk 内容...

[文档：yyy.md，片段 2]
chunk 内容...
```

这样做的好处：

- 方便模型理解不同来源
- 方便后续展示引用来源
- 方便排查回答依据

## 7. 多轮对话需求

多轮对话不是简单把全部历史消息都传给模型，需要控制上下文长度。

V4 阶段推荐策略：

- 默认携带最近 5 到 10 条消息
- 超出长度的历史消息不传入
- 每次仍然基于当前问题进行向量检索
- 历史消息只作为对话上下文，不替代知识库检索

可选优化：

- 对历史消息做摘要
- 使用独立的问题改写步骤，将追问改写成完整问题

## 8. SSE 流式响应需求

### 为什么使用 SSE

AI 回复可能需要几秒到几十秒，如果等完整回答生成后再返回，用户体验较差。

SSE 可以让后端边生成边返回，前端实时展示，效果类似 ChatGPT。

### SSE 数据格式

建议事件类型：

- `message`：AI 回复片段
- `done`：回复完成
- `error`：异常信息

示例：

```text
event: message
data: 这是第一段回复

event: message
data: 这是第二段回复

event: done
data: [DONE]
```

### 异常处理

需要处理：

- 模型调用失败
- SSE 连接中断
- 用户取消请求
- 检索无结果
- Token 超限

## 9. 前端聊天页面需求

### 页面布局

推荐布局：

- 左侧：会话列表
- 顶部：当前知识库选择器
- 中间：消息列表
- 底部：输入框和发送按钮

### 消息展示

消息展示要求：

- 用户消息右侧展示
- AI 消息左侧展示
- AI 回复支持 Markdown
- 代码块支持高亮
- 流式输出时逐字或逐段追加
- 回复中展示加载状态

### 输入区

输入区要求：

- 支持多行输入
- Enter 发送
- Shift + Enter 换行
- 发送时禁用按钮
- 问题不能为空

## 10. 后端接口建议

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/conversations` | 会话列表 |
| POST | `/api/conversations` | 新建会话 |
| GET | `/api/conversations/{id}` | 会话详情 |
| DELETE | `/api/conversations/{id}` | 删除会话 |
| GET | `/api/conversations/{id}/messages` | 消息列表 |
| POST | `/api/chat` | 普通问答接口 |
| GET | `/api/chat/stream` | SSE 流式问答接口 |

如果使用 POST 发起 SSE 不方便，可以采用：

- POST 创建问题请求
- GET 根据请求 ID 建立 SSE

或使用 `fetch` 读取流式响应。

## 11. 数据库设计

### conversation 会话表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 用户 ID |
| knowledge_base_id | bigint | 知识库 ID |
| title | varchar | 会话标题 |
| last_message | varchar | 最近消息摘要 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除 |

### message 消息表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| conversation_id | bigint | 会话 ID |
| user_id | bigint | 用户 ID |
| role | varchar | USER / ASSISTANT / SYSTEM |
| content | longtext | 消息内容 |
| model_name | varchar | 模型名称 |
| prompt_tokens | int | 输入 Token |
| completion_tokens | int | 输出 Token |
| total_tokens | int | 总 Token |
| created_at | datetime | 创建时间 |

### message_reference 消息引用表，可选

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| message_id | bigint | AI 消息 ID |
| document_id | bigint | 来源文档 ID |
| chunk_id | bigint | 来源 Chunk ID |
| similarity | decimal | 相似度 |
| created_at | datetime | 创建时间 |

## 12. 引用来源展示，可选

为了增强可信度，可以在 AI 回答下方展示参考来源。

展示内容：

- 文档名称
- Chunk 片段摘要
- 相似度
- 点击查看原文片段

这不是 V4 必须功能，但非常适合作为简历亮点。

## 13. V4 验收标准

- 用户可以新建聊天会话
- 用户可以选择知识库进行提问
- 系统可以保存用户消息
- 系统可以基于问题检索 TopK Chunk
- 系统可以组装 Prompt 并调用大模型
- AI 回复可以通过 SSE 流式返回
- 前端可以实时展示 AI 回复
- AI 回复支持 Markdown 和代码块
- 会话历史可以保存和查看
- 用户只能访问自己的会话

## 14. 面试讲法

V4 阶段可以这样讲：

用户提问时，系统会先将问题转换为 Embedding 向量，然后在当前知识库范围内通过 pgvector 检索最相关的 TopK 文档片段。系统把这些片段作为上下文，与用户问题和最近几轮历史对话一起组装成 Prompt，再调用大语言模型生成答案。为了提升交互体验，后端使用 SSE 将模型输出流式返回给前端，前端实时渲染 Markdown 内容，效果类似 ChatGPT。

如果面试官追问多轮对话，可以回答：

- 系统会保存 conversation 和 message，实现历史会话管理。
- 每次提问都会重新进行知识库检索，保证回答仍然基于私有知识。
- 历史消息只传最近几轮，避免上下文过长导致 Token 超限。
- 后续可以增加问题改写或对话摘要来提升追问效果。
