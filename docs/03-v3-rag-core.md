# V3 RAG 核心需求文档

## 1. 阶段目标

V3 是整个 Knowledge AI 项目的核心阶段，目标是在文档管理基础上实现 RAG 文档入库能力。

用户上传文档后，系统需要能够读取文档内容，将长文本切分为多个 Chunk，调用 Embedding 模型生成向量，并将文本片段和向量保存到 PostgreSQL + pgvector 中。完成这一阶段后，系统就具备了基于私有文档进行语义检索的基础能力。

## 2. 核心流程

```text
用户上传 PDF / DOCX / TXT / Markdown
  |
  v
文档状态变为 UPLOADED
  |
  v
用户点击解析或系统自动触发解析
  |
  v
下载或读取 MinIO 中的原始文件
  |
  v
DocumentReader 提取文本
  |
  v
TokenTextSplitter 文本切块
  |
  v
EmbeddingModel 生成向量
  |
  v
VectorStore / Mapper 保存到 pgvector
  |
  v
更新文档状态、Chunk 数量和解析结果
```

## 3. 文档解析需求

### 支持格式

V3 建议优先支持：

- PDF
- TXT
- Markdown
- DOCX

### 解析方式

PDF：

- 使用 Spring AI `PdfDocumentReader`
- 读取 PDF 正文内容
- 忽略空白页和空白文本

TXT：

- 使用普通文本读取
- 注意编码问题，默认 UTF-8

Markdown：

- 按普通文本读取
- 保留标题、列表、代码块等文本结构

DOCX：

- 可以使用 Apache POI
- 或使用 Apache Tika 统一抽取文本

### 解析状态

解析状态建议：

- `UPLOADED`：已上传，未解析
- `PARSING`：解析中
- `PARSED`：解析成功
- `FAILED`：解析失败

状态更新要求：

- 开始解析前更新为 `PARSING`
- 解析成功后更新为 `PARSED`
- 解析失败后更新为 `FAILED`
- 失败时记录错误信息

## 4. 文本切块需求

### 为什么需要切块

不能直接把整个 PDF 喂给大模型，主要原因有：

- PDF 内容可能非常长，超过模型上下文窗口限制
- 长文档全部输入会导致 Token 成本高
- 大量无关内容会干扰模型回答
- 用户提问通常只需要文档中的局部片段
- 切块后可以按语义相似度检索最相关内容

### 切块策略

推荐配置：

- 单个 Chunk：500 到 1000 token
- Chunk Overlap：50 到 150 token
- 保留 Chunk 顺序
- 记录所属文档和知识库
- 保存 Chunk 元数据

### Chunk 元数据

每个 Chunk 建议保存：

- 知识库 ID
- 文档 ID
- Chunk 顺序
- Chunk 文本
- Token 数量
- 页码，可选
- 段落信息，可选
- 文件名
- 创建时间

## 5. Embedding 向量化需求

### Embedding 作用

Embedding 用于将文本转换为向量，使系统可以基于语义相似度进行检索。

相比关键词搜索，Embedding 检索的优势是：

- 可以理解语义相近但字面不同的问题
- 可以支持自然语言提问
- 可以从大量 Chunk 中找到最相关片段
- 更适合 RAG 问答场景

### Embedding 流程

```text
Chunk 文本
  |
  v
EmbeddingModel
  |
  v
高维向量
  |
  v
保存到 pgvector
```

### 模型选择

可选模型：

- OpenAI Embedding
- Qwen Embedding
- DeepSeek 兼容 Embedding
- Ollama 本地 Embedding

简历项目建议优先接入一个云端模型，后续 V5 再扩展多模型。

## 6. pgvector 存储需求

### 使用 PostgreSQL + pgvector 的原因

pgvector 是 PostgreSQL 的向量扩展，适合简历项目使用，原因是：

- 可以直接在 PostgreSQL 中存储向量
- 支持向量相似度检索
- 支持和业务字段一起过滤
- 本地 Docker 部署简单
- 比单独引入 Milvus、Elasticsearch 更轻量

### 检索能力

需要支持：

- 根据知识库 ID 过滤
- 根据文档 ID 过滤
- 根据用户问题向量检索相似 Chunk
- 按相似度排序
- 返回 TopK 结果
- 删除文档时删除对应 Chunk

### 相似度计算

pgvector 常见距离类型：

- Cosine Distance
- Inner Product
- L2 Distance

RAG 场景常用余弦相似度。

## 7. 数据库设计

### document_chunk 表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| knowledge_base_id | bigint | 知识库 ID |
| document_id | bigint | 文档 ID |
| chunk_index | int | Chunk 顺序 |
| content | text | Chunk 文本 |
| embedding | vector | 文本向量 |
| token_count | int | Token 数量 |
| metadata | jsonb | 扩展元数据 |
| created_at | timestamp | 创建时间 |
| updated_at | timestamp | 更新时间 |

### document 表新增关注字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| parse_status | varchar | 解析状态 |
| chunk_count | int | Chunk 数量 |
| error_message | text | 失败原因 |
| parsed_at | datetime | 解析完成时间 |

## 8. 后端接口建议

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/documents/{id}/parse` | 触发文档解析 |
| POST | `/api/documents/{id}/reparse` | 重新解析文档 |
| GET | `/api/documents/{id}/parse-status` | 查询解析状态 |
| GET | `/api/documents/{id}/chunks` | 查询文档 Chunk |
| DELETE | `/api/documents/{id}/chunks` | 删除文档向量数据 |

## 9. 后端模块建议

### RagParseService

负责文档解析入口。

职责：

- 校验文档是否存在
- 更新解析状态
- 调用文档读取器
- 调用文本切块器
- 调用向量化服务
- 保存 Chunk
- 更新文档解析结果

### DocumentReaderService

负责不同文件类型的文本读取。

职责：

- PDF 文本读取
- TXT 文本读取
- Markdown 文本读取
- DOCX 文本读取
- 返回统一文本结构

### ChunkService

负责文本切块。

职责：

- 调用 TokenTextSplitter
- 处理空白文本
- 保存 Chunk 顺序
- 计算 Chunk 数量

### EmbeddingService

负责向量生成。

职责：

- 调用 EmbeddingModel
- 处理模型异常
- 支持批量 Embedding
- 返回向量结果

### VectorStoreService

负责向量存储和检索。

职责：

- 保存 Chunk 和向量
- 删除文档 Chunk
- 按知识库检索相似 Chunk
- 返回相似度分数

## 10. 异步解析设计

文档解析和 Embedding 可能耗时较长，建议使用异步任务处理。

简单实现方式：

- 用户上传后手动点击解析
- 后端接口立即返回
- 使用 `@Async` 异步执行解析
- 前端轮询解析状态

后续扩展方式：

- 使用消息队列
- 使用任务表
- 使用定时任务补偿失败任务

简历项目中使用 `@Async` 已经足够展示异步处理思路。

## 11. 前端页面需求

文档列表中需要展示：

- 解析状态
- 解析进度
- Chunk 数量
- 解析失败原因
- 解析按钮
- 重新解析按钮

Chunk 查看页面可以展示：

- Chunk 顺序
- Chunk 文本预览
- Token 数量
- 来源文档

## 12. V3 验收标准

- PDF 文档可以被读取为文本
- TXT 和 Markdown 可以被读取为文本
- 文本可以被切分为多个 Chunk
- Chunk 可以生成 Embedding 向量
- Chunk 和向量可以保存到 pgvector
- 文档解析状态可以正确流转
- 解析失败时可以记录错误信息
- 前端可以看到解析状态和 Chunk 数量
- 删除文档时可以同步删除 Chunk 数据

## 13. 面试讲法

V3 阶段可以这样讲：

项目上传文档后不会直接把整份文档交给大模型，而是先通过 Spring AI 读取文档内容，再使用 TokenTextSplitter 将长文本切成多个 Chunk。每个 Chunk 会调用 Embedding 模型转换成向量，并保存到 PostgreSQL + pgvector 中。用户后续提问时，系统可以通过向量相似度从大量 Chunk 中召回最相关的内容，作为 RAG 问答的上下文。

如果面试官追问为什么这样设计，可以回答：

- 大模型上下文有限，完整 PDF 可能超出 Token 限制。
- 即使能放进去，完整文档也会带来高成本和大量噪声。
- Embedding 能把文本转成语义向量，让系统支持自然语言语义检索。
- pgvector 可以把向量检索和知识库 ID、文档 ID 等业务过滤条件结合起来，比较适合中小型知识库项目。
