# 01 全站模块 Key 清单（契约）

> 本文件在实现前冻结首批 `module` / `key`。  
> 实现按 P0～P5 分期接入，但**前后端字段名以此为准**。  
> 总览见 [00-overview.md](./00-overview.md)。

## 1. 约定

| 项 | 规则 |
| --- | --- |
| module | 小写英文，稳定枚举，见下表 |
| key | 点分小写，模块内唯一，如 `agent.enabled` |
| value_type | `string` / `int` / `long` / `bool` / `double` / `json` |
| sensitive | `true` 时加密存储、响应脱敏；更新空串表示「保持不变」 |
| 默认值来源 | schema 声明的 default，对齐当前 `application.yml` / Properties |
| 读取优先级 | DB（同 module+key）→ YAML/Properties → schema default |

### 模块枚举

| module | 中文名 | 接入阶段 | 说明 |
| --- | --- | --- | --- |
| `site` | 站点 | P0 | 站点展示与维护 |
| `security` | 安全 | P0 | 注册与访问策略 |
| `upload` | 上传 | P0 | 附件大小与扩展名等业务级限制 |
| `integration` | 集成与密钥 | P1 | AI / 外部服务 baseUrl、apiKey、业务路径 |
| `chat` | 角色聊天 | P2 | Agent / 分段 / 图片转述 / 附件缓存（行为参数） |
| `tts` | 语音 | P2 | TTS 默认行为（连接见 integration） |
| `knowledge` | 知识库 / RAG | P3 | 切块与检索参数（连接见 integration） |
| `reading` | 精读工作台 | P3 | 蒸馏与发布默认策略 |
| `blog` | 博客 | P4 | 发布与展示默认 |
| `study` | 学习系统 | P4 | 专注 / 习惯等默认 |
| `diary` | 日记 | P4 | 隐私与导出默认 |
| `app` | 应用生成 | P4 | 生成与部署业务开关（路径见 integration） |
| `ops` | 运维开关 | P5 | 调试错误暴露等；审计/健康走独立 API |

> 多模型 CRUD 表、`prompt` 模板中心：**本期不建独立 module**，见延后项。单通道连接配置由 `integration` 承担。

---

## 2. `site`（P0）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `name` | string | `Ai Scene` | 站点名称 |
| `slogan` | string | `` | 一句话介绍 |
| `maintenance_mode` | bool | `false` | 维护模式（开启后非 admin 禁用写操作；细节 P0 实现时定） |
| `frontend_public_notice` | string | `` | 前端公告文案（可空） |

## 3. `security`（P0）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `register_enabled` | bool | `true` | 是否开放注册 |
| `default_user_role` | string | `user` | 新用户默认角色（仅允许白名单值） |
| `login_fail_hint_generic` | bool | `true` | 登录失败是否统一模糊提示 |

> Session / JWT 密钥、Cookie 域名等**不进入**本表。

## 4. `upload`（P0）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `max_file_size_mb` | int | `20` | 业务上传上限（MB），对齐 `upload.max-file-size` |
| `allowed_image_ext` | string | `jpg,jpeg,png,gif,webp` | 图片扩展名白名单（逗号分隔） |
| `presign_expire_seconds` | int | `3600` | 预签名下载过期 |

> 物理目录、访问 baseUrl 见 `integration`（P1）。

---

## 5. `integration`（P1）

> 详见 [03-ai-integrations.md](./03-ai-integrations.md)。  
> 凡 `sensitive=true` 的字段：DB 加密、GET 脱敏、空值不覆盖。

### 5.1 知识库 AI

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `knowledge.ai.base_url` | string |  | DashScope 兼容地址 | 对齐 `knowledge.ai.base-url` |
| `knowledge.ai.api_key` | string | yes | （空则回落 env） | Knowledge AI Key |

### 5.2 Jina

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `knowledge.jina.base_url` | string |  | `https://r.jina.ai/` | Jina Reader |
| `knowledge.jina.api_key` | string | yes | `` | 若启用 Jina 鉴权时使用（可空） |
| `knowledge.tavily.base_url` | string |  | `https://api.tavily.com` | Tavily Search（备用） |
| `knowledge.tavily.api_key` | string | yes | `` | Tavily Key（备用） |
| `knowledge.deepseek.base_url` | string |  | `https://api.deepseek.com/anthropic` | DeepSeek Anthropic 端（联网） |
| `knowledge.deepseek.api_key` | string | yes | `` | DeepSeek 官方 Key（≠ DashScope） |

### 5.3 聊天 / Agent / 图片转述

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `chat.agent.base_url` | string |  | DashScope 兼容地址 | Agent 模型 endpoint |
| `chat.agent.api_key` | string | yes |  | 可与 DashScope 共用或单独配 |
| `chat.image_caption.base_url` | string |  | DashScope 兼容地址 | 图片转述 |
| `chat.image_caption.api_key` | string | yes |  | 对齐 `CHAT_IMAGE_CAPTION_API_KEY` |
| `chat.segmentation.base_url` | string |  | （可空=复用 agent） | 分段模型 endpoint，可选 |
| `chat.segmentation.api_key` | string | yes |  | 可选；空则复用 agent key |

### 5.4 代码生成

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `codegen.base_url` | string |  |  | 对齐 `CODEGEN_API_BASE_URL` |
| `codegen.api_key` | string | yes |  | 对齐 `CODEGEN_API_KEY` |

### 5.5 AstrBot

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `astrbot.base_url` | string |  | `http://127.0.0.1:6185` | AstrBot 服务地址 |
| `astrbot.api_key` | string | yes |  | AstrBot API Key |

### 5.6 GPT-SoVITS / TTS 连接与路径

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `tts.base_url` | string |  | `http://127.0.0.1:9880` | GPT-SoVITS 地址 |
| `tts.ref_audio_upload_dir` | string |  | `./tmp/tts-ref/` | 参考音频上传目录 |
| `tts.seed_voice.ref_audio_path` | string |  |  | seed 参考音频本地路径 |
| `tts.seed_voice.prompt_text` | string |  |  | seed 提示文本 |

### 5.7 上传与部署路径

| key | type | sensitive | default 建议 | 说明 |
| --- | --- | --- | --- | --- |
| `upload.path` | string |  | `./tmp/uploads/` | 上传物理目录 |
| `upload.base_url` | string |  | `http://localhost:8123/api` | 上传访问前缀 |
| `app.deploy.host` | string |  |  | 部署访问 host 展示/拼接 |
| `app.deploy.code_output_dir` | string |  |  | 代码输出目录 |
| `app.deploy.code_deploy_dir` | string |  |  | 部署目录 |

### 5.8 MinIO（可选）

默认可不实现；若实现，建议：

| key | type | sensitive | 说明 |
| --- | --- | --- | --- |
| `knowledge.minio.enabled` | bool |  | 是否启用 |
| `knowledge.minio.endpoint` | string |  | MinIO endpoint |
| `knowledge.minio.access_key` | string | yes | access key |
| `knowledge.minio.secret_key` | string | yes | secret key |
| `knowledge.minio.bucket_documents` | string |  | 文档 bucket |

---

## 6. `chat`（P2）

对齐现有 `chat.*` 行为参数（连接与密钥见 `integration`）。

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `agent.enabled` | bool | `true` | 是否启用 Agent |
| `agent.max_steps` | int | `8` | Agent 最大步数 |
| `agent.history_limit` | int | `20` | 历史条数上限 |
| `agent.l2_enabled` | bool | `false` | L2 开关 |
| `attachment_cache_ttl_minutes` | int | `120` | 附件缓存 TTL |
| `image_caption.enabled` | bool | `true` | 图片转述开关 |
| `image_caption.model_name` | string | `qwen3.6-flash` | 转述模型名 |
| `image_caption.prompt` | string | （现 YAML 中文 prompt） | 转述 Prompt |
| `image_caption.timeout_seconds` | int | `30` | 转述超时 |
| `segmentation.enabled` | bool | `true` | 朗读分段开关 |
| `segmentation.style` | string | `natural` | 分段风格 |
| `segmentation.min_length` | int | `15` | 最短段长 |
| `segmentation.max_segments` | int | `8` | 最大段数 |
| `segmentation.temperature` | double | `0.1` | 分段模型温度 |
| `segmentation.max_tokens` | int | `256` | 分段 max tokens |
| `segmentation.timeout_seconds` | int | `5` | 分段超时 |
| `segmentation.delay_base` | double | `0.35` | 段间延迟基数 |
| `segmentation.delay_per_char` | double | `0.015` | 每字延迟 |
| `segmentation.delay_max` | double | `1.2` | 延迟上限 |
| `segmentation.fallback_to_rules` | bool | `true` | 失败回落规则分段 |

## 7. `tts`（P2）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `enabled` | bool | `true` | 是否对前端暴露 TTS |
| `default_text_lang` | string | `zh` | 默认合成语言 |
| `default_prompt_lang` | string | `zh` | 默认参考音频语言 |
| `seed_voice.enabled` | bool | `false` | 是否启用内置 seed 音色 |
| `seed_voice.name` | string | `达妮娅` | seed 展示名 |
| `connect_timeout_ms` | int | `5000` | 连接超时 |
| `read_timeout_ms` | int | `120000` | 读超时 |
| `ref_audio.max_size_mb` | int | `10` | 参考音频上限 |

---

## 8. `knowledge`（P3）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `ai.chat_model` | string | `deepseek-v3` | 知识库聊天默认模型名 |
| `ai.embedding_model` | string | `text-embedding-v2` | Embedding 模型名 |
| `ai.embedding_dimension` | int | `1536` | 向量维度（变更需提示重建索引） |
| `ai.temperature` | double | `0.2` | 聊天温度 |
| `ai.max_tokens` | int | `2048` | 最大 tokens |
| `ai.timeout_seconds` | int | `120` | 请求超时 |
| `rag.top_k` | int | `5` | 检索 TopK |
| `rag.chunk_size` | int | `1200` | 切块大小 |
| `rag.chunk_overlap` | int | `150` | 切块重叠 |
| `jina.timeout_seconds` | int | `30` | Jina 抓取超时 |

## 9. `reading`（P3）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `distill.temperature` | double | `0.2` | 蒸馏温度 |
| `distill.max_tokens` | int | `4096` | 蒸馏 max tokens |
| `distill.system_prompt` | string | （与现有蒸馏 Prompt 对齐） | 系统提示词 |
| `publish.default_as_draft` | bool | `true` | 发布博客默认草稿 |
| `publish.ask_open_editor` | bool | `true` | 发布后是否提示跳转编辑器 |
| `redistill.confirm_required` | bool | `true` | 前端是否强制二次确认 |
| `search.provider` | string | `deepseek` | 搜索提供方：`deepseek` / `tavily` / `placeholder` |
| `ingest.sync_mode` | string | `sync` | V1/V2 同步；预留 `async` |

---

## 10. `blog`（P4）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `list.page_size_default` | int | `10` | 默认列表页大小 |
| `post.summary_max_length` | int | `200` | 摘要最大长度 |
| `post.allow_like` | bool | `true` | 是否允许点赞 |
| `post.view_count_enabled` | bool | `true` | 是否统计阅读 |
| `editor.default_status` | string | `DRAFT` | 新建文章默认状态 |

## 11. `study`（P4）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `focus.default_minutes` | int | `25` | 默认专注时长 |
| `focus.break_minutes` | int | `5` | 默认休息时长 |
| `habit.reminder_enabled_default` | bool | `true` | 新习惯是否默认提醒 |
| `stats.default_range_days` | int | `7` | 统计默认天数 |
| `workspace.show_checklist` | bool | `true` | 工作台是否默认展示清单 |

## 12. `diary`（P4）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `privacy.default_private` | bool | `true` | 新日记默认私密 |
| `export.enabled` | bool | `false` | 是否开放导出（预留） |
| `list.page_size_default` | int | `20` | 列表默认页大小 |

## 13. `app`（P4）

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `codegen.enabled` | bool | `true` | 是否开放应用生成 |
| `codegen.default_type` | string | （与现有枚举对齐） | 默认生成类型 |
| `deploy.enabled` | bool | `true` | 是否开放一键部署入口 |
| `deploy.public_host_display` | string | （可空，展示用） | 部署访问前缀展示文案 |

## 13.1 `ops`（P5）

运维开关（非业务模块）。审计 / 健康走独立 API，不在此表。

| key | type | default 建议 | 说明 |
| --- | --- | --- | --- |
| `debug_expose_error_detail` | bool | `false` | 是否对 admin 暴露更细的系统错误信息 |
| `usage_log_enabled` | bool | `false` | AI 调用用量落库总开关（见 ops-observability Phase A） |
| `usage_log_retain_days` | int | `30` | 用量日志保留天数 |
| `ops_audit_enabled` | bool | `true` | 通用操作审计写入开关（Phase B） |
| `ops_audit_retain_days` | int | `90` | 操作审计保留天数 |
| `biz_stats_enabled` | bool | `true` | 业务日统计写入开关（Phase C） |
| `http_log_enabled` | bool | `false` | HTTP 访问日志总开关（Phase D） |
| `http_log_mode` | string | `errors_only` | `errors_only` / `slow_and_errors` / `all` |
| `http_log_slow_ms` | int | `1000` | 慢请求阈值 ms |
| `http_log_retain_days` | int | `14` | HTTP 日志保留天数 |

---

## 14. 明确排除（全阶段）

| 类别 | 示例 |
| --- | --- |
| 基础设施连接 | MySQL / Redis / PostgreSQL（向量库）URL、账号密码 |
| 安全根密钥 | JWT / Session 签名密钥、`SITE_SETTING_CRYPTO_SECRET` |
| 用户偏好 | 主题、个人默认知识库等（本期不做） |
| 专项延后 | 多模型行级 CRUD 表、Prompt 模板库 |

> 第三方 AI Key、AstrBot、TTS 地址、业务路径已纳入 `integration`，**不再排除**。

---

## 15. Key 变更流程

1. 先改本文件，注明阶段与 default。
2. 同步后端 schema 注册与前端标签文案。
3. 若删除 / 重命名 key：写迁移说明（旧 key 是否兼容读取一轮）。

禁止只在前端或只在代码里新增未登记的 key。
