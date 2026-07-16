# 04 P2：聊天与 TTS 设置接入

> 依赖 P0；**强烈建议先完成 P1**（[03-ai-integrations.md](./03-ai-integrations.md)）。  
> Key 见 [01-module-keys.md](./01-module-keys.md) 的 `chat` / `tts`（行为参数）。  
> AstrBot / GPT-SoVITS / Caption 的 baseUrl、apiKey 在 `integration`，本阶段不重复配置。

## 1. 阶段目标

```text
设置中心接入 chat、tts 行为参数
  → 业务运行时从 SiteSettingService 读参数
  → 前端设置页两个 Tab 可改且生效
```

## 2. 后端

### 2.1 注册模块

新增：

- `ChatSettingModule`（code=`chat`）
- `TtsSettingModule`（code=`tts`）

schema 与 default **必须与 01 清单一致**。

### 2.2 业务接线（必须）

| 设置 key | 接线位置（示意） |
| --- | --- |
| `chat.agent.*` | Agent 编排：`ChatAgentProperties` 改为经 Setting 读取，或 Facade 包装 |
| `chat.image_caption.*` | 图片转述服务开关 / prompt / timeout / modelName |
| `chat.segmentation.*` | 分段服务参数与延迟 |
| `chat.attachment_cache_ttl_minutes` | 附件缓存 TTL |
| `tts.enabled` | TTS Controller 或服务入口总开关 |
| `tts.default_*` / `seed_voice.*` /超时 / `ref_audio.max_size_mb` | TTS 合成与上传参考音频校验 |

### 2.3 生效策略

- 每次请求或短缓存读取 Setting（推荐模块级缓存 + 变更失效）。
- 禁止只改 DB、业务仍读启动期注入的 `@ConfigurationProperties` 可变字段却不刷新。
- `onChanged("chat")`：清理 chat 相关本地缓存（若有）。

### 2.4 API

沿用 P0 通用接口，无需新路径：

```text
GET/PUT  /admin/site-settings/chat
GET      /admin/site-settings/chat/schema
POST     /admin/site-settings/chat/reset
（tts 同理）
```

`GET /modules` 中 `chat` / `tts` 标记为可写。

## 3. 前端

### 3.1 侧栏

启用模块：

- 角色聊天 `chat`
- 语音 TTS `tts`

建议表单分组（同一 module 内用 `group` 元数据，若 schema 支持）：

**chat**

- Agent
- 图片转述
- 朗读分段
- 附件

**tts**

- 总开关与语言
- Seed 音色
- 超时与参考音频限制

### 3.2 交互

- `segmentation.prompt` 类长文本用 TextArea。
- 改 `agent.enabled` / `tts.enabled`：Confirm 后保存。
- 保存成功后，聊天页 / TTS 页**不强制刷新整站**；下次请求用新值即可。可选：提示「已保存，对新会话生效」。

### 3.3 联调用例

1. 关闭 `chat.agent.enabled` → 聊天路径不再走 Agent（或返回明确未启用）。
2. 修改 `image_caption.prompt` → 新上传图片转述结果文风变化。
3. 关闭 `tts.enabled` → 前端 TTS 入口隐藏或接口 403/业务错误。
4. 调小 `ref_audio.max_size_mb` → 超限上传被拒。

## 4. 验收清单

### 后端

- [ ] chat / tts schema 覆盖 01 清单全部 key。
- [ ] 关闭 agent / tts 后行为符合预期。
- [ ] reset 后回到 YAML 默认。
- [ ] 连接信息从 `integration`（或 YAML 回落）读取，不在本模块重复暴露密钥字段。

### 前端

- [ ] 两模块表单可完整编辑与保存。
- [ ] 分组清晰，长 Prompt 可编辑。
- [ ] 与真实聊天 / TTS 流程联调通过。
- [ ] 密钥类入口引导至「集成与密钥」页，避免两套表单。

## 5. 明确不做（P2）

- 音色资源 CRUD 管理台（沿用现有 TTS 接口即可，设置页只做默认参数）。
- 在本模块再次配置 apiKey / baseUrl（属于 P1 `integration`）。
- 多模型选择器、Prompt 模板库。
- knowledge / reading 接线。
