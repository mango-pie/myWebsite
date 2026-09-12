# 07 V3 领域知识树 — 后端（DDL 与数据约定）

> 产品需求：[05-v3-agent-path.md](./05-v3-agent-path.md)  
> **状态**：DDL + REST / 门闩 / Service **已实现**（一期）。前端联调见 [08-v3-frontend-sync.md](./08-v3-frontend-sync.md)。

## 1. 迁移

- Flyway：`src/main/resources/db/migration/V15__learning_domain_tree.sql`
- 三表：`learning_domain`、`learning_branch`、`learning_leaf`
- 无主键自增、无 DB 外键；ID 由应用 Snowflake 生成（与 `knowledge_note` 一致）

## 2. 表说明

### 2.1 `learning_domain`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | Snowflake |
| user_id | BIGINT | 所属用户 |
| name | VARCHAR(128) | 领域名，如 Java |
| sort_order | INT | 多领域排序 |
| create_time / update_time | DATETIME | 审计 |
| is_delete | TINYINT | 逻辑删 |

**唯一**：`uk_learning_domain_user_name (user_id, name, is_delete)` — 同一用户下仅一条未删的同名领域。

### 2.2 `learning_branch`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | Snowflake |
| domain_id | BIGINT | 所属领域 |
| user_id | BIGINT | 冗余，权限/快照 |
| parent_branch_id | BIGINT | **0** = L1；否则为 L1 枝 id（L2） |
| depth | TINYINT | **1** 或 **2**（应用校验） |
| title | VARCHAR(128) | 主题名 |
| sort_order | INT | 同父排序 |

**唯一**：`uk_learning_branch_sibling (domain_id, parent_branch_id, title, is_delete)` — 同父下枝名不重复。

### 2.3 `learning_leaf`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK | Snowflake |
| domain_id | BIGINT | 冗余 |
| branch_id | BIGINT | 挂载枝 |
| user_id | BIGINT | 冗余 |
| knowledge_note_id | BIGINT | 挂载 note |
| sort_order | INT | 同枝内排序 |
| attached_at | DATETIME | 确认挂叶时间 |

**唯一**：`uk_learning_leaf_note (knowledge_note_id)` — 全局一篇 note 至多一行（含已逻辑删行；重新挂同一 note 应 **UPDATE** 复活，勿 INSERT 第二行）。

## 3. 业务规则与 FR 映射

| 规则 | 实现位置 |
| --- | --- |
| FR-01 领域名唯一 | `uk_learning_domain_user_name` + Service |
| FR-22 同父枝不重复 | `uk_learning_branch_sibling` |
| FR-32 一 note 一挂载 | `uk_learning_leaf_note` |
| FR-71 有叶枝不可删 | Service：`COUNT(learning_leaf WHERE branch_id=? AND is_delete=0)` |
| FR-72 删 domain 不删 note | 仅逻辑删 domain/branch/leaf |
| FR-73 合并枝 | 事务内 UPDATE leaf.branch_id，逻辑删 A |

## 4. `leafCount` 约定（PRD §5.5）

- **不存表**；快照与 UI 数字 = **该枝直接挂载**的 note 数：

```sql
SELECT COUNT(*) FROM learning_leaf
 WHERE branch_id = ? AND is_delete = 0;
```

- L1 枝标题旁 `(N)` **不含** L2 子枝下的叶（一期）。

## 5. 查询示例

### 5.1 枝快照（门闩 / 回挂，≤200 条）

```sql
SELECT id, domain_id, parent_branch_id, depth, title, sort_order
FROM learning_branch
WHERE domain_id = ? AND is_delete = 0
ORDER BY parent_branch_id, sort_order, id;
```

应用层组装 `path`（如 `Java > 集合`）与每枝 `leafCount`（§4）。

### 5.2 某枝下 note 列表（复习右栏）

```sql
SELECT l.*, n.title, n.publish_status, n.index_status
FROM learning_leaf l
JOIN knowledge_note n ON n.id = l.knowledge_note_id AND n.is_delete = 0
WHERE l.branch_id = ? AND l.is_delete = 0
ORDER BY l.sort_order, l.attached_at DESC;
```

### 5.3 未挂任何树的 note（回挂入口 FR-92）

```sql
SELECT n.*
FROM knowledge_note n
WHERE n.is_delete = 0 AND n.status = 'SUCCESS'
  AND NOT EXISTS (
    SELECT 1 FROM learning_leaf l
    WHERE l.knowledge_note_id = n.id AND l.is_delete = 0
  )
  AND n.source_document_id IN (
    SELECT id FROM source_document sd WHERE sd.user_id = ? AND sd.is_delete = 0
  );
```

（具体过滤可按管理员精读列表 API 对齐。）

## 6. Java 骨架

| 类型 | 路径 |
| --- | --- |
| Entity | `com.ai.model.entity.knowledge.LearningDomain` / `LearningBranch` / `LearningLeaf` |
| Mapper | `com.ai.mapper.knowledge.LearningDomainMapper` 等，`BaseMapper` |

## 7. REST API（已实现）

基路径：`/api/admin/knowledge/learning`（与全局 context-path 一致；Controller 映射 `/admin/knowledge/learning`）。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/domains` | 领域列表 |
| POST | `/domains` | 创建领域 |
| PUT | `/domains/{id}` | 更新 |
| DELETE | `/domains/{id}` | 级联逻辑删枝与叶 |
| GET | `/domains/{domainId}/tree` | 树快照 |
| POST | `/domains/{domainId}/branches` | 建枝 |
| PUT | `/branches/{id}` | 改枝 |
| DELETE | `/branches/{id}` | 删空枝 |
| POST | `/branches/{sourceId}/merge` | 合并枝 |
| POST | `/gate` | AI 门闩，返回 `gatePassId`（仅 related=true） |
| POST | `/leaves/attach` | 挂叶 |
| PUT | `/leaves/move` | 移叶 |
| DELETE | `/leaves/by-note/{noteId}` | 取消挂载 |
| GET | `/branches/{branchId}/leaves` | 枝下 note 列表 |
| GET | `/domains/{domainId}/unattached-notes` | 未挂树 note |
| POST | `/leaves/batch-suggest` | AI 批量挂枝建议 |

### 7.1 gatePass（Redis 优先，内存降级，TTL 30 分钟）

- Key：`learning:gate:{gatePassId}`（`app.redis.available=true` 且 Redis 可用时）
- 签发：`POST /gate` 在 `related=true` 时返回 `gatePassId`
- 消费：树模式下 `search/preview`、`ingest/batch-url` 传 `domainId` + `gatePassId`，且 pass 须为 **LEAF** 意图
- 空枝补学：`skipGate=true` + `branchId` + `domainId`，跳过门闩（FR-51）；**仅当该枝当前无叶**，否则 4xx
- Redis 不可用或写入失败时回退进程内 `ConcurrentHashMap`（单实例/开发环境）

### 7.2 V2 请求扩展字段

`KnowledgeSearchPreviewRequest` / `KnowledgeIngestBatchUrlRequest` 增加：

- `domainId`、`gatePassId`、`branchId`、`skipGate`
- 当 `domainId != null`：`search/preview` **不生成** outline（FR-80）

## 8. Java 实现

| 类型 | 路径 |
| --- | --- |
| Service | `LearningTreeServiceImpl`、`LearningTreeGateServiceImpl` |
| Controller | `LearningAdminController` |
| 门闩缓存 | `LearningGatePassStore`（`LearningTreeRedisConstant`） |

## 9. 测试

默认可跑的 Mockito 单测（不依赖 DB/Redis）：

- `LearningTreeServiceImplTest`：删有叶枝、同名领域、attach 复活、删 domain 级联、merge、空枝 skipGate
- `LearningGatePassStoreTest`：内存签发/校验、Redis 读写、Redis 失败降级内存
- `LearningTreeGateServiceImplTest`：`validateTreeModeV2Access`（skipGate / gatePass）