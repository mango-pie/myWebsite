# 全站设置中心 P4 — 前端对接说明（博客 / 学习 / 日记 / 应用）

> 依赖 P0。后端已实现 `blog`、`study`、`diary`、`app` 行为参数模块。  
> 产品说明见 [06-p4-content-business.md](./06-p4-content-business.md)。  
> **Codegen Key / 部署物理目录** 仍在 `integration`，本页不重复配置。

## 1. 侧栏（P4 完成后应齐全）

```text
站点设置
├── 站点 / 安全 / 上传
├── 集成与密钥
├── 角色聊天 / 语音 TTS
├── 知识库 / RAG / 精读工作台
├── 博客          blog     → /admin/settings/blog
├── 学习系统      study    → /admin/settings/study
├── 日记          diary    → /admin/settings/diary
└── 应用生成      app      → /admin/settings/app
```

以 `GET /api/admin/site-settings/modules` 动态渲染侧栏，避免写死分期。

## 2. API（沿用 P0）

| 方法 | 路径 |
| --- | --- |
| GET | `/api/admin/site-settings/{module}/schema` |
| GET | `/api/admin/site-settings/{module}` |
| PUT | `/api/admin/site-settings/{module}` |
| POST | `/api/admin/site-settings/{module}/reset` |

`module` = `blog` | `study` | `diary` | `app`。

## 3. 表单要点

| 模块 | UI |
| --- | --- |
| blog | 开关 + Select（`editor.default_status`：DRAFT / PUBLISHED / OFFLINE） |
| study | 数字（专注/休息/统计天数）+ 开关 |
| diary | 隐私默认 + 导出开关 + 列表页大小 |
| app | 生成/部署总开关（Confirm）；`codegen.default_type` Select（`html` / `multi_file`）；部署展示前缀 |

`danger` 字段保存前 Confirm：`post.allow_like`、`codegen.enabled`、`deploy.enabled`。

## 4. 业务侧消费

| 设置 | 前端注意 |
| --- | --- |
| `blog.post.allow_like=false` | 隐藏点赞；接口也会拒绝 |
| `blog.post.view_count_enabled=false` | 隐藏阅读上报；接口拒绝 |
| `study.habit.reminder_enabled_default` | **仅前端**：新建习惯表单默认勾选（后端无 reminder 字段） |
| `study.workspace.show_checklist` | 工作台 VO 含 `showChecklist`，据此渲染清单区 |
| `study.stats.default_range_days` | 统计页未选日期时后端用该区间；也可预填日期控件 |
| `diary.export.enabled` | 关闭时隐藏导出按钮（暂无导出 API） |
| `diary.privacy.default_private` | 新建日记默认 status：私密→草稿(0)，否则完成(1) |
| `app.codegen.enabled=false` | 隐藏创建/生成；接口拒绝 |
| `app.deploy.enabled=false` | 隐藏部署入口；接口拒绝 |
| `app.deploy.public_host_display` | 部署成功 URL 前缀展示；空则回落 integration host |

列表 `pageSize`：可不传或传 `<=0`，后端用各模块 `list.page_size_default`。

## 5. Key 清单

完整契约见 [01-module-keys.md](./01-module-keys.md) 第 10～13 节。
