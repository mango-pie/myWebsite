# V2 知识库与文档管理需求文档

## 1. 阶段目标

V2 的目标是在基础后台之上加入知识库和文档管理能力，为后续 RAG 文档解析、向量化入库和 AI 问答做准备。

这一阶段的核心不是 AI，而是完成“知识从哪里来”的业务闭环。用户需要能够创建知识库、上传文档、查看文档、删除文档，并且系统需要将文件统一保存到 MinIO，将文档元数据保存到 MySQL。

## 2. 业务范围

V2 主要包含两个业务模块：

- 知识库管理
- 文档管理

知识库用于组织文档，文档是后续 RAG 入库的原始数据来源。

## 3. 知识库管理需求

### 新增知识库

用户可以创建一个新的知识库。

创建时需要填写：

- 知识库名称
- 知识库描述
- 可见范围

校验规则：

- 知识库名称不能为空
- 同一用户下知识库名称不建议重复
- 描述长度需要限制
- 默认状态为启用

### 编辑知识库

用户可以修改自己创建的知识库。

可修改内容：

- 知识库名称
- 知识库描述
- 可见范围
- 状态

### 删除知识库

用户可以删除自己的知识库。

删除规则：

- 如果知识库下存在文档，建议二次确认
- 删除知识库时需要同步处理文档元数据
- V2 阶段可以先做逻辑删除
- V3 后需要同步删除对应 Chunk 和向量数据

### 查询知识库

用户可以查看自己的知识库列表。

列表展示：

- 知识库名称
- 知识库描述
- 文档数量
- 可见范围
- 状态
- 创建时间
- 更新时间

## 4. 文档管理需求

### 上传文档

用户可以向指定知识库上传文档。

支持文件类型：

- PDF
- DOCX
- TXT
- Markdown

上传校验：

- 文件不能为空
- 文件大小需要限制，例如单文件不超过 50MB
- 文件扩展名需要校验
- 文件 MIME 类型可以辅助校验
- 用户必须拥有目标知识库权限

### 查看文档

用户可以查看指定知识库下的文档列表。

列表展示：

- 文件名
- 文件类型
- 文件大小
- 上传人
- 上传时间
- 解析状态
- Chunk 数量
- 操作按钮

### 删除文档

用户可以删除文档。

删除规则：

- 删除 MySQL 文档元数据
- 删除或标记删除 MinIO 文件
- V3 后需要同步删除 document_chunk 向量数据
- 建议先实现逻辑删除，降低误删风险

### 下载文档

用户可以下载原始文档。

实现方式：

- 后端生成 MinIO 预签名下载地址
- 或后端代理下载文件流

推荐简历项目中使用预签名 URL，能体现对象存储的实际使用经验。

## 5. 文件上传流程

```text
Vue 前端选择文件
  |
  v
Axios multipart/form-data 上传
  |
  v
Spring Boot 接收 MultipartFile
  |
  v
校验文件类型、大小、权限
  |
  v
生成对象存储 Key
  |
  v
上传文件到 MinIO
  |
  v
MySQL 保存文档元数据
  |
  v
返回文档记录
```

## 6. MinIO 存储设计

### Bucket 规划

建议创建以下 Bucket：

- `knowledge-documents`：存储知识库文档
- `user-avatars`：存储用户头像

### Object Key 设计

推荐文档 Key 格式：

```text
documents/{userId}/{knowledgeBaseId}/{yyyyMMdd}/{uuid}.{ext}
```

示例：

```text
documents/10001/20001/20260710/7f2a9b8c.pdf
```

这样设计的好处：

- 避免文件名冲突
- 方便按用户和知识库定位文件
- 方便后续做生命周期管理
- 不直接暴露用户原始文件名

## 7. 文档状态设计

V2 阶段虽然还没有真正解析文档，但数据库字段要提前为 V3 做准备。

建议文档解析状态：

- `UPLOADED`：已上传
- `PARSING`：解析中
- `PARSED`：解析成功
- `FAILED`：解析失败

V2 上传成功后默认状态为 `UPLOADED`。

## 8. 后端接口建议

### 知识库接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/knowledge-bases` | 知识库分页查询 |
| POST | `/api/knowledge-bases` | 新增知识库 |
| GET | `/api/knowledge-bases/{id}` | 知识库详情 |
| PUT | `/api/knowledge-bases/{id}` | 修改知识库 |
| DELETE | `/api/knowledge-bases/{id}` | 删除知识库 |

### 文档接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/knowledge-bases/{kbId}/documents` | 查询知识库文档 |
| POST | `/api/knowledge-bases/{kbId}/documents` | 上传文档 |
| GET | `/api/documents/{id}` | 文档详情 |
| DELETE | `/api/documents/{id}` | 删除文档 |
| GET | `/api/documents/{id}/download-url` | 获取下载地址 |

## 9. 数据库设计

### knowledge_base 知识库表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| name | varchar | 知识库名称 |
| description | varchar | 知识库描述 |
| user_id | bigint | 创建用户 |
| visibility | varchar | 可见范围 |
| status | tinyint | 状态 |
| document_count | int | 文档数量 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除标识 |

### document 文档表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| knowledge_base_id | bigint | 知识库 ID |
| user_id | bigint | 上传用户 |
| file_name | varchar | 原始文件名 |
| file_type | varchar | 文件类型 |
| file_size | bigint | 文件大小 |
| bucket_name | varchar | MinIO Bucket |
| object_key | varchar | MinIO Object Key |
| file_url | varchar | 文件访问地址 |
| parse_status | varchar | 解析状态 |
| chunk_count | int | Chunk 数量 |
| error_message | text | 解析失败原因 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |
| deleted | tinyint | 逻辑删除标识 |

## 10. 前端页面需求

### 知识库列表页

页面包含：

- 搜索框
- 新增知识库按钮
- 知识库卡片或表格
- 文档数量展示
- 编辑按钮
- 删除按钮
- 进入详情按钮

### 知识库详情页

页面包含：

- 知识库基础信息
- 文档上传区域
- 文档列表
- 文件类型标签
- 文件大小展示
- 解析状态展示
- 下载按钮
- 删除按钮

### 文档上传组件

上传组件要求：

- 支持拖拽上传
- 支持点击选择文件
- 显示上传进度
- 限制文件类型
- 限制文件大小
- 上传成功后刷新文档列表

## 11. V2 验收标准

- 用户可以创建知识库
- 用户可以编辑知识库
- 用户可以删除知识库
- 用户可以查看知识库列表和详情
- 用户可以上传 PDF、DOCX、TXT、Markdown 文件
- 文件可以成功保存到 MinIO
- MySQL 可以保存文档元数据
- 用户可以查看知识库下的文档列表
- 用户可以删除文档
- 文档状态默认为已上传

## 12. 面试讲法

V2 阶段可以这样讲：

项目中文档文件没有直接存入 MySQL，而是统一上传到 MinIO 对象存储，MySQL 只保存文件名、文件大小、文件类型、Bucket、Object Key、解析状态等元数据。这样可以避免数据库存储大文件带来的性能问题，也方便后续迁移到云对象存储。

如果面试官继续追问，可以补充：

- 文件上传时会校验用户是否拥有目标知识库权限，避免越权上传。
- 文件 Object Key 使用用户 ID、知识库 ID、日期和 UUID 组合生成，避免文件名冲突。
- 文档表提前设计了解析状态和 Chunk 数量字段，方便 V3 阶段接入 RAG 文档解析流程。
