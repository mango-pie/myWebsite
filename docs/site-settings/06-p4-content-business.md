# 06 P4：博客 / 学习 / 日记 / 应用生成

> 依赖 P0。Key 见 [01-module-keys.md](./01-module-keys.md) 的 `blog` / `study` / `diary` / `app`。  
> 部署目录、codegen endpoint 见 P1 `integration`；本阶段只管业务开关与默认值。

## 1. 阶段目标

```text
设置中心补齐内容与业务侧模块
  → 博客、学习、日记、应用生成的默认策略可后台配置
  → 前端设置页模块齐全（除运维 P5）
```

本阶段后，管理员「站点设置」侧栏覆盖全站首批业务模块。

## 2. 后端

### 2.1 注册模块

- `BlogSettingModule`
- `StudySettingModule`
- `DiarySettingModule`
- `AppSettingModule`

### 2.2 业务接线

| 模块 | 重点接线 |
| --- | --- |
| `blog` | 列表默认 pageSize、点赞/阅读开关、新建默认状态、摘要长度（若有生成逻辑） |
| `study` | 专注默认时长/休息、统计默认区间、工作台 checklist 展示默认 |
| `diary` | 新建默认私密、列表 pageSize、export 开关占位 |
| `app` | 生成入口开关、默认 codeGenType、部署入口开关、公开展示 host 文案 |

部署物理目录、codegen API Key 等在 `integration` 配置（或 YAML 回落）。

### 2.3 枚举对齐

以下 key 的允许值必须与现有后端枚举一致，并在 schema 用 `enum` 声明：

- `blog.editor.default_status`
- `app.codegen.default_type`

实现前对照：`BlogPost` 状态枚举、`App` / codeGenType 枚举，避免设置了非法值。

### 2.4 API

仍用通用接口，无需新 Controller 资源路径（除非现有业务已有独立配置接口，应逐步弃用并文档标明）。

## 3. 前端

### 3.1 侧栏完整信息架构（P4 结束时）

```text
站点设置
├── 站点          site
├── 安全          security
├── 上传          upload
├── 集成与密钥    integration
├── 角色聊天      chat
├── 语音 TTS      tts
├── 知识库 / RAG  knowledge
├── 精读工作台    reading
├── 博客          blog
├── 学习系统      study
├── 日记          diary
└── 应用生成      app
```

### 3.2 各 Tab 要点

| 模块 | UI 要点 |
| --- | --- |
| blog | 开关类居多；默认状态用 Select |
| study | 数字输入（时长、天数）+ 开关 |
| diary | 隐私默认 + 导出预留（关闭时前端隐藏导出按钮） |
| app | 生成/部署总开关；关闭时对应菜单入口隐藏或禁用 |

### 3.3 全局体验

- 模块列表以 `/modules` 为准动态渲染，避免前端写死「已完成分期」导致漏模块。
- 各业务页面读取「功能是否启用」时：可调用 `GET /{module}` 中的单个开关，或后续加聚合接口（P4 可不做聚合，避免超范围）。

## 4. 联调用例

1. `blog.post.allow_like=false` → 点赞接口拒绝或前端隐藏。
2. `study.focus.default_minutes=50` → 新建专注默认 50 分钟。
3. `diary.privacy.default_private=true` → 新建日记默认私密。
4. `app.codegen.enabled=false` → 非 admin 无法创建生成任务（具体策略与现权限模型对齐）。

## 5. 验收清单

### 后端

- [ ] 四模块 schema 覆盖 01 清单。
- [ ] 开关类配置真实影响对应 API / 默认值。
- [ ] 非法枚举值 PUT 失败并返回字段错误。
- [ ] 部署目录 / codegen key 不在本四模块重复暴露（在 integration）。

### 前端

- [ ] 侧栏含 `integration` 在内的全部业务模块均可进入（含 P0～P3）。
- [ ] 各模块保存 / 重置可用。
- [ ] 关闭 app/blog 相关能力后，菜单或按钮状态正确。

## 6. 明确不做（P4）

- 用户偏好中心。
- 多模型行级 CRUD / Prompt 模板库。
- 审计与完整健康面板（P5）。
- 学习提醒推送通道、日记云同步等产品级新功能（设置只提供已有能力的默认项）。
