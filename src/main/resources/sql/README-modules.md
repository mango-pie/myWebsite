# 模块 → 数据表 / Schema 映射（运维清单）

配合 `app.modules.*` 开关使用。**关闭某模块不需要删表**——只是相关 API 不再注册、对应 Mapper 不再扫描；表可保留。新环境部署时，可只执行 platform + 你启用模块对应的 schema。

> 说明：`schema_core.sql` 里混装了 platform 与若干模块的建表语句（历史原因）。只要启用了 blog / app-lab / chat 任一模块，就需要执行它；即便都不启用，其中的 `user` 属于 platform，也建议执行。

## Platform（始终执行）

| Schema 文件 | 表 |
| --- | --- |
| `schema_core.sql` | `user`（platform）、`app`、`chat_history`、`blog_*`（见下方各模块说明） |
| `site_setting_schema.sql` | `site_setting` |
| `site_setting_audit_schema.sql` | `site_setting_audit` |

## 各模块

| 模块 (`app.modules.*`) | Schema 文件 | 主要表 |
| --- | --- | --- |
| `blog` | `schema_core.sql` | `blog_category`, `blog_tag`, `blog_post`, `blog_post_tag`, `blog_image` |
| `app-lab` | `schema_core.sql` | `app` |
| `chat` | `schema_core.sql` + `chat_conversation_schema.sql` | `chat_history`, `chat_conversation`, `chat_message` |
| `knowledge` | `knowledge_schema.sql` (+ `knowledge_note_v1_alter.sql`) | `knowledge_base`, `knowledge_document`, `source_document`, `knowledge_note`, `knowledge_processing_task`, `knowledge_conversation`, `knowledge_message`, `knowledge_message_reference` |
| `knowledge`（向量库，PG） | `knowledge_pgvector_schema.sql` | `knowledge_document_chunk`（PostgreSQL/pgvector） |
| `reading` | `knowledge_reading_job_schema.sql` | `knowledge_reading_job` |
| `study` | `study_schema.sql` | `study_list`, `study_task`, `study_task_checklist`, `study_focus_session`, `study_habit`, `study_habit_check_log` |
| `diary` | `diary_schema.sql` | `diary_entry` |
| `tts` | `tts_schema.sql` | `tts_voice_profile` |
| `ops` | `ops_audit_log_schema.sql`, `ai_usage_log_schema.sql`, `biz_stat_daily_schema.sql`, `http_access_log_schema.sql` | `ops_audit_log`, `ai_usage_log`, `biz_stat_daily`, `http_access_log` |

## 最小安装集示例

- **仅后台 + 博客**：platform 三件套 + `schema_core.sql`（blog 表），其余可不建。
- **精读工作台（无独立向量库）**：platform + `knowledge_schema.sql`（+ `knowledge_note_v1_alter.sql`）+ `knowledge_reading_job_schema.sql`；发博客还需 blog 表。
- **关闭 ops**：可不建 `ops_audit_log` / `ai_usage_log` / `biz_stat_daily` / `http_access_log`；对应服务由 NoOp 实现静默丢弃，业务写操作不受影响。

## 跨模块注意

- `knowledge` 发布博客走 `NoteBlogPublisher` SPI；`blog` 关闭时 `publish-blog` / `sync-blog` 明确报「博客模块未启用」，不访问 `blog_*` 表。
- `study` 同步博客草稿走 `BlogDraftReader` SPI；`blog` 关闭时该操作报错，不访问 `blog_post`。
- Integration 连通性探测对 TTS / 向量库 / AstrBot 为弱依赖（`ObjectProvider`），对应模块关闭时该探测项返回「已跳过 / 未启用」。
