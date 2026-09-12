# 数据库索引与表盘点（2026-09-12）

> 范围：本机真实库 `aiscene`（39 表）× v0.10.0 代码交叉核对。只读盘点，落地仅限「零回归风险」项（V20）。

## 一、已落地：冗余索引清理（V20）

三个单列索引被同表复合索引**完全前缀覆盖**，查询可无损改走复合索引，删除只减少写放大：

| 已删索引 | 覆盖它的复合索引 |
| --- | --- |
| `chat_history.idx_appId (appId)` | `idx_appId_createTime (appId, createTime)` |
| `blog_post_tag.idx_post_id (post_id)` | `uk_post_tag (post_id, tag_id)` |
| `site_setting.idx_module (module)` | `uk_module_key (module, setting_key)` |

已在真实 schema 克隆上验证：两遍执行幂等，三个索引均被删除。
（注意 `blog_image.idx_post_id` 未删——它没有可覆盖它的复合索引，独立保留。）

## 二、保留（有意不删）

- `ai_usage_log` / `http_access_log` / `ops_audit_log` 各自的 `idx_create_time` 与 `(维度, create_time)` 复合索引互不构成前缀，保留清理任务按时间扫描的通路。
- `chat_conversation` 的 `idx_user_default` 与 `idx_user_config` 共享前三列但第四列各有所用（定位默认会话 / 按最后消息排序），都保留。
- `blog_post` 的 `idx_status`/`idx_is_top`/`idx_user_id`/`idx_category_id` 等低选择性索引：单表写入频率低（个人博客），收益太小不值得动；待有慢查询证据再说。
- `user.idx_userName`：管理端按昵称检索在用。

## 三、Parked 表（代码已不在本分支，数据保留）

以下 5 张表在 v0.10.0 代码中**零引用**，属于被还原的特性线（见还原现场说明），
数据完整保留、**不删**；若日后从 stash 恢复对应特性线，其迁移（V15~V18）可直接接管：

| 表 | 所属特性线 | 迁移 |
| --- | --- | --- |
| `learning_domain` / `learning_branch` / `learning_leaf` | V3 领域知识树 | V15 |
| `pet_device` | 宠物设备 | V17 |
| `worklog_entry` | 工作日志 | V18 |

注意：全新空库（仅 V1~V14+V19+V20）不会创建这 5 张表；真实存量库已有。两态均与各自代码一致。

## 四、核对过的非孤表

`knowledge_processing_task`、`chat_history`、`app` 均有实体/Mapper 在用，非孤表。
