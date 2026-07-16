# 05 P3：知识库与精读设置接入

> 依赖 P0；**建议已完成 P1**（Knowledge AI / Jina 连接）。  
> Key 见 [01-module-keys.md](./01-module-keys.md) 的 `knowledge` / `reading`。  
> 业务能力文档仍见 `docs/ai-reading-workbench/` 与 `docs/KNOWLEDGE_INTEGRATION.md`；本阶段只做**可运营参数**接入（apiKey/baseUrl 见 `integration`）。

## 1. 阶段目标

```text
设置中心接入 knowledge、reading
  → RAG / 蒸馏 / 发布默认策略可后台调整
  → 前端两个 Tab 可用
```

## 2. 后端

### 2.1 注册模块

- `KnowledgeSettingModule`（`knowledge`）
- `ReadingSettingModule`（`reading`）

### 2.2 业务接线

| 设置 key | 接线 |
| --- | --- |
| `knowledge.rag.*` | 切块服务、检索 TopK（替代仅读 `KnowledgeRagProperties` 常量路径） |
| `knowledge.ai.temperature` 等 | 知识库聊天 / 蒸馏共用的模型运行参数（模型名可配；api-key/baseUrl 见 integration） |
| `knowledge.ai.embedding_dimension` | 写入校验 + 变更副作用提示 |
| `knowledge.jina.timeout_seconds` | URL 抓取客户端超时 |
| `reading.distill.*` | 精读蒸馏调用 |
| `reading.publish.*` | 发布博客默认草稿等 |
| `reading.search.provider` | 搜索预览策略：`tavily`（默认）/ `placeholder`（与工作台 V2 对齐） |
| `reading.ingest.sync_mode` | 预留；V1/V2 保持 sync |

### 2.3 高风险项

`knowledge.ai.embedding_dimension`、`rag.chunk_size` / `chunk_overlap`：

- schema 增加 `danger: true` 或 `sideEffect` 文案。
- `onChanged`：不自动重建索引；返回/日志提示「需对已有文档手动重建」。
- 前端强确认。

### 2.4 API

通用接口即可：

```text
/admin/site-settings/knowledge
/admin/site-settings/reading
```

## 3. 前端

### 3.1 侧栏

启用：

- 知识库 / RAG `knowledge`
- 精读工作台 `reading`

### 3.2 表单分组建议

**knowledge**

- 模型运行参数（名称 / 温度 / tokens / 超时）
- RAG 切块与 TopK
- Jina 超时

**reading**

- 蒸馏参数与系统 Prompt
- 发布默认行为
- 搜索 / 采集占位项（可折叠「高级」）

### 3.3 与工作台页面关系

- 精读工作台业务页**不复制**一套设置表单；统一跳转到 `/admin/settings/reading`。
- 知识库管理页如需「检索预览参数」，可读当前设置只读展示，编辑仍进设置中心。

### 3.4 联调用例

1. 修改 `rag.top_k` → 知识库问答引用片段数量变化。
2. 修改 `distill.system_prompt` → 新采集精炼风格变化。
3. `publish.default_as_draft=true` → 工作台发布默认勾选草稿。
4. 修改 `embedding_dimension` → 前端风险确认；后端不自动删向量。

## 4. 验收清单

### 后端

- [ ] knowledge / reading schema 覆盖 01 清单。
- [ ] RAG / 蒸馏路径实际读取设置值。
- [ ] 本模块不重复暴露 api-key；向量库连接仍不可通过设置 API 读写。
- [ ] 危险参数变更有 sideEffect 信息（响应 message 或 schema 字段）。

### 前端

- [ ] 两模块可编辑；危险项二次确认。
- [ ] 长 Prompt 编辑体验可用。
- [ ] 与精读工作台、知识库问答联调通过。
- [ ] 连接配置引导至「集成与密钥」。

## 5. 明确不做（P3）

- 在本模块配置 apiKey / baseUrl（属于 P1）。
- 多模型供应商行级管理、Prompt 模板库（延后专项）。
- 异步任务队列、批量重建索引工具（可另开运维工具）。
- blog / study 等 P4 模块接线。
