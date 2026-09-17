# 交接：前端已在等待的三组缺失端点（v0.10.0 巡检发现）

> 来源：2026-09-12 前端页面巡检（qa-designer 管理员登录态，全部模块开启）
> 前端已对这些 404 做 `silent404` 静默降级（页面显示空态/本地模式），不再弹全局报错；
> 但以下端点属于既定需求文档范围，后端补齐后前端即刻可用。

## 1. 精读任务列表 GET /admin/knowledge/reading-jobs
- `KnowledgeAdminController` 目前只有 `GET /reading-jobs/{jobId}` 单查；
- 前端 `KnowledgeNoteListPage`（我的文章）已轮询调用 `GET /admin/knowledge/reading-jobs`
  （分页参数 `API.KnowledgeReadingJobQueryRequest`），用于在列表页跟踪进行中的精读任务。
- 参考：docs/ai-reading-workbench/06-v2-frontend-sync.md（V2 任务跟踪）。

## 2. 工作日志云同步 /worklog/**
- 前端 `src/api/worklogController.ts` 已实现并接线：
  - `GET /worklog/list?limit=` → `WorkLogApiEntry[]`
  - `GET /worklog/by-date?date=`
  - `POST /worklog/save`（body: workDate/title/done/problem/summary/plan/tags[]）
  - `POST /worklog/delete`（body: { date }）
  - `POST /worklog/import`（body: { overwrite, entries[] } → { imported, skipped }）
- 本地 IndexedDB 已完整工作；端点 404 时页面显示「云端待命」。

## 3. EchoBot 桌宠设备绑定 /pet/devices
- 前端 `src/api/petDeviceController.ts` 已实现并接线：
  - `GET /pet/devices` → PetDeviceVO 列表
  - `POST /pet/devices`（body: { deviceName, clientInfo }）→ 明文 token 仅返回一次
  - `POST /pet/devices/revoke`（body: { id }）
- 404 时个人信息页保持「尚未绑定设备」空态。

-- 前端会话（ZCode）2026-09-12

## 4. 资料库模块 /library/**（2026-09-14 新增，整模块待实现）

> 来源：资料库（资料储藏室）前端页面实现，设计稿 `AI-frontend/docs/prototypes/library-prototype-v3.html`。
> 前端已完成：页面 `src/pages/library/`、数据层 `src/composables/useLibrary.ts`、
> API 客户端 `src/api/libraryController.ts`（全部 `silent404`）。
> `/library/**` 404 时页面自动回退为本地演示数据（页面显示「演示数据 · 后端待接入」角标）；
> 后端按下方契约实现后，刷新页面即切换真实数据，前端无需再改动。

### 4.0 模块开关
- 前端已在 `MODULE_KEYS` 注册 `library`（标签「资料库」，路由前缀 `/library`）；
- 后端 `GET /app/modules` 需返回 `"library": true` 才会放行路由守卫与导航入口
  （建议纳入 site_setting 模块开关体系，与 blog/knowledge 等同规格）。

### 4.1 数据模型
- `LibraryFile`：`{ id, name, ext（小写扩展名）, category: DOCUMENT|IMAGE|AUDIO|VIDEO|CODE|ARCHIVE|OTHER, size, uploadedAt: 'yyyy-MM-dd HH:mm', tagIds[], collectionIds[], aiStatus: NONE|PENDING|DONE|FAILED, aiSummary?, sha256?, objectKey? }`
- `LibraryTag`：`{ id, name, color: sakura|violet|lilac|blue|mint|sun }`
- `LibraryCollection`（合集/书脊）：`{ id, name, color }`
- `LibraryRecycleFile`：LibraryFile 字段 + `deletedAt: 'yyyy-MM-dd'`（保留 30 天）
- `LibrarySummary`：`{ fileCount, usedBytes, quotaBytes, recentFileId?, recentFileAt? }`

### 4.2 端点契约
| 方法 | 路径 | 入参 | 返回 data |
|---|---|---|---|
| GET | `/library/summary` | — | LibrarySummary（容量统计 + 最近取出） |
| GET | `/library/files` | query: keyword / category / collectionId / tagIds / sort(time,name,size) / dir(asc,desc) | LibraryFile[]（前端当前全量拉取后本地筛选，参数已预留） |
| GET | `/library/recycle` | — | LibraryRecycleFile[] |
| GET | `/library/tags` | — | LibraryTag[] |
| POST | `/library/tags` | body: { name, color? } | LibraryTag |
| GET | `/library/collections` | — | LibraryCollection[] |
| POST | `/library/files/upload-init` | body: { fileName, size, sha256 } | `{ mode:'instant', file }`（SHA-256 命中秒传）或 `{ mode:'direct', fileId, uploadUrl, objectKey }` |
| POST | `/library/files/upload-complete` | body: { fileId } | LibraryFile（登记入库，触发 AI 解析队列） |
| GET | `/library/files/{id}/download-url` | — | `{ url, expiresIn }`（MinIO 预签名，3600s），同时记为一次「取出」 |
| POST | `/library/files/{id}/open` | — | boolean（刷新最近取出） |
| POST | `/library/files/delete` | body: { ids[] } | boolean（软删除进回收站） |
| POST | `/library/recycle/restore` | body: { ids[] } | boolean |
| POST | `/library/recycle/purge` | body: { ids[] } | boolean（MinIO 对象同步清理，不可恢复） |
| POST | `/library/files/{id}/tags` | body: { tagIds[] } | boolean（覆盖式更新） |
| POST | `/library/files/collections` | body: { fileIds[], collectionId } | boolean |
| POST | `/library/files/{id}/transfer-knowledge` | — | boolean（转入知识库：切块 → 向量化 → 可 RAG 问答） |
| POST | `/library/files/{id}/ai-regenerate` | — | boolean（重新生成 AI 摘要，异步任务，完成后 aiStatus DONE） |

### 4.3 实现要点（源自设计稿已确认的产品语义）
- 存储：MinIO bucket（前端展示名 `library-files`），对象键形如 `library/{userId}/yyyyMM/{sha256}.{ext}`；
- 上传：单文件 ≤ 50MB 走常规上传，大文件预签名 PUT 直传；上传前前端计算 SHA-256 用于秒传去重；
- 回收站：软删除保留 30 天，到期定时任务清理；
- AI：文件入库后排队解析（aiStatus PENDING → DONE），生成摘要与建议标签；转知识库复用 knowledge 模块的摄取管线。

### 4.4 前后端联调结果（2026-09-14）
后端 LibraryController 实现与 4.2 契约**完全一致**，已用真实会话逐端点验证通过：
- summary / files / recycle / tags / collections GET 全部正常（数字字段序列化为字符串，前端已兼容）；
- 建标签 POST /library/tags ✓；upload-init（direct 预签名直传）→ PUT → upload-complete 全链路 ✓；
- 秒传：同 SHA-256 再次 upload-init 返回 `mode:'instant'` ✓；
- delete → recycle → restore → purge 回环 ✓（purge/restore 对不在回收站的文件返回 data:false，前端已兼容）；
- download-url（预签名 3600s）✓、open（刷新最近取出）✓；
- MinIO 未启动时 upload-init 返回 50001「资料库存储桶不可用」，前端 toast 兜底正常。

**缺口（请后端补一个端点）：`POST /library/collections`（新建合集）**
- 契约：body `{ name, color? }` → 返回 `LibraryCollection`；前端「新建合集」入口已留好，
  收到该端点后把 `useLibrary` 里的占位 toast 换成真实调用即可（前端跟进，约 10 行改动）。

### 4.5 编辑类端点契约（2026-09-14 追加，前端已全部接线、按 404/405 优雅降级）
前端 `useLibrary` 已实现下列动作并在 UI 挂好（侧栏悬停编辑/删除、抽屉移出合集、文件重命名）；
端点未实现时前端 toast「待后端接入」，后端补齐后**无需改前端**即可用：

| 方法 | 路径 | 入参 | 返回 data | 说明 |
|---|---|---|---|---|
| POST | `/library/collections` | `{ name, color? }` | LibraryCollection | 新建合集 |
| PUT | `/library/tags/{id}` | `{ name, color? }` | LibraryTag | 标签重命名（文件上的标签同步生效） |
| DELETE | `/library/tags/{id}` | — | boolean | 删除标签，需同步剥离所有文件上的该标签 |
| PUT | `/library/collections/{id}` | `{ name }` | boolean | 合集重命名 |
| DELETE | `/library/collections/{id}` | — | boolean | 删除合集（文件保留、仅解除归属） |
| POST | `/library/files/{id}/collections/remove` | `{ collectionId }` | boolean | 文件移出合集 |
| POST | `/library/files/{id}/rename` | `{ name }` | LibraryFile | 文件重命名 |

约定：路径已注册但方法未映射时 Spring 返回 405（如当前的 POST /library/collections），
前端把 404/405 同样视为「未实现」，因此**新增端点直接按上表路径+方法注册即可，无需通知前端**。

**联调环境备注**：本机 MinIO 由 Docker 容器 `qa-minio`（9000/9001，minioadmin/minioadmin）提供；
QA 账号 `qa-library` / `qa12345678`（user 角色，id 990000000000000001）。

-- 前端会话（ZCode）2026-09-14
