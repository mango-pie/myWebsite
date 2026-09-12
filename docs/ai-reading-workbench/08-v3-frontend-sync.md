# 08 V3 领域知识树 — 前端联调说明

> 产品 PRD：[05-v3-agent-path.md](./05-v3-agent-path.md)  
> 后端 DDL/API：[07-v3-backend.md](./07-v3-backend.md)  
> V2 合蒸仍见 [06-v2-frontend-sync.md](./06-v2-frontend-sync.md)

## 1. 页面职责（建议）

| 区域 | 行为 |
| --- | --- |
| 领域选择 | 列出 `GET /learning/domains`，选中当前 `domainId`（localStorage） |
| 左树 | `GET /learning/domains/{id}/tree?includeLeaves=false`，展示枝与 `leafCount`；0 叶显示「去搜一篇」 |
| 右栏 | 选中枝 → `GET /learning/branches/{branchId}/leaves` |
| 问答/门闩 | `POST /learning/gate`，据 `related` / `intent` 控制是否开放搜文 |
| 挂叶确认 | 合蒸完成后 `POST /learning/leaves/attach` |

未选 domain 时：不走树模式 V2（无 `domainId` 的 preview/batch-url 与现 V2 相同）。

## 2. 基路径与鉴权

与 V1/V2 相同：管理员 Session，`/api/admin/knowledge/learning/**`（以项目 `context-path` 为准）。

## 3. 领域与树 CRUD

```http
GET    /api/admin/knowledge/learning/domains
POST   /api/admin/knowledge/learning/domains     { "name": "Java", "sortOrder": 0 }
PUT    /api/admin/knowledge/learning/domains/{id}
DELETE /api/admin/knowledge/learning/domains/{id}
```

```http
GET  /api/admin/knowledge/learning/domains/{domainId}/tree?includeLeaves=false
POST /api/admin/knowledge/learning/domains/{domainId}/branches
     { "title": "集合", "parentBranchId": 0, "sortOrder": 0 }
PUT  /api/admin/knowledge/learning/branches/{id}
DELETE /api/admin/knowledge/learning/branches/{id}
POST /api/admin/knowledge/learning/branches/{sourceId}/merge
     { "targetBranchId": 123 }
```

`tree` 响应：`domain`、`branches[]`（含 `path`、`leafCount`）、`snapshotTruncated` / `snapshotMessage`。  
`includeLeaves=true` 时每个 branch 带 `leaves[]`（可选，一般右栏单独拉 leaves 即可）。

## 4. 门闩（必须先于树模式搜文）

```http
POST /api/admin/knowledge/learning/gate
```

```json
{
  "domainId": 1001,
  "question": "HashMap 扩容原理"
}
```

响应要点：

| 字段 | 说明 |
| --- | --- |
| `related` | false → 只展示 `answer` / `hints`，**禁用**搜文按钮 |
| `intent` | `CHAT_ONLY` / `BRANCH` / `LEAF` |
| `gatePassId` | 仅 `related=true` 时有；30 分钟内有效 |
| `suggestedBranchId` / `suggestedBranchTitle` | LEAF 挂叶默认值 |

**BRANCH**：提示用户确认建枝（调 `POST .../branches`），**不要**调 preview/batch-url。  
**LEAF**：保存 `gatePassId`，进入 V2 链路。

## 5. 树模式 V2（LEAF）

在 [06](./06-v2-frontend-sync.md) 的请求体上 **增加**：

```json
{
  "goal": "HashMap 扩容",
  "domainId": 1001,
  "gatePassId": "abc..."
}
```

```http
POST /api/admin/knowledge/search/preview
POST /api/admin/knowledge/ingest/batch-url
```

- 有 `domainId` 时 **无** `outline` 字段（FR-80）。
- 无合法 `gatePassId` 或 intent 非 LEAF → 4xx。

### 5.1 空枝补学（跳过门闩）

从「还没学 · 去搜一篇」进入（**仅当该枝当前无叶**；已有叶时不可 `skipGate`，须先走门闩）：

```json
{
  "goal": "Java 并发入门",
  "domainId": 1001,
  "branchId": 2002,
  "skipGate": true
}
```

## 6. 挂叶与回挂

合蒸 job 成功拿到 `noteId` 后：

```http
POST /api/admin/knowledge/learning/leaves/attach
```

```json
{
  "domainId": 1001,
  "noteId": 9001,
  "branchId": 2002
}
```

无合适枝时：

```json
{
  "domainId": 1001,
  "noteId": 9001,
  "newBranchTitle": "集合",
  "parentBranchId": 0
}
```

回挂历史 note：

```http
GET  /api/admin/knowledge/learning/domains/{domainId}/unattached-notes?pageNum=1&pageSize=20
POST /api/admin/knowledge/learning/leaves/batch-suggest
     { "domainId": 1001, "notes": [{ "noteId": 1, "title": "...", "summary": "..." }] }
```

用户确认后逐条或批量 `attach`。

其它：

```http
PUT    /api/admin/knowledge/learning/leaves/move     { "noteId", "targetBranchId" }
DELETE /api/admin/knowledge/learning/leaves/by-note/{noteId}
```

## 7. 推荐联调顺序（LEAF 全链）

1. `POST /learning/domains` → 记下 `domainId`  
2. `POST /learning/gate` → `intent=LEAF` → 记下 `gatePassId`  
3. `POST /knowledge/search/preview`（带 `domainId` + `gatePassId`）→ 勾选 URL  
4. `POST /knowledge/ingest/batch-url` → 轮询 `reading-jobs/{jobId}` → `noteId`  
5. `POST /learning/leaves/attach`  
6. `GET /learning/domains/{id}/tree` → 对应枝 `leafCount` +1  

## 8. 前端状态建议

- `related === false`：隐藏/禁用「搜文生成」「batch-url」  
- `intent === BRANCH`：展示建枝确认，禁用搜文  
- `intent === LEAF` 且已有 `gatePassId`：开放 V2  
- 合蒸完成但未 attach：note 在「未入树」列表仍可见  

## 9. 限制（一期）

- `gatePassId`：生产环境在 Redis 开启时**多实例共享**、TTL 30 分钟；未开 Redis 时为进程内存，**重启失效**、单实例  
- 树模式与无 domain 的精读采集 **并行**，互不强制  
