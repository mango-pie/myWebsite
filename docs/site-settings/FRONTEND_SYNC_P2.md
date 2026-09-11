# 全站设置中心 P2 — 前端对接说明（聊天 / TTS）

> 依赖 P0；建议先完成 P1。后端已实现 `chat`、`tts` 行为参数模块。  
> 产品说明见 [04-p2-chat-tts.md](./04-p2-chat-tts.md)。  
> **密钥 / baseUrl** 仍在 `integration`，本页不重复配置。

## 1. 侧栏

在「集成与密钥」之后增加：

| 模块 | 路由建议 | displayName |
| --- | --- | --- |
| `chat` | `/admin/settings/chat` | 角色聊天 |
| `tts` | `/admin/settings/tts` | 语音 TTS |

`GET /api/admin/site-settings/modules` 中两者 `writable=true`、`phase=P2`。

## 2. API（沿用 P0）

| 方法 | 路径 |
| --- | --- |
| GET | `/api/admin/site-settings/{module}/schema` |
| GET | `/api/admin/site-settings/{module}` |
| PUT | `/api/admin/site-settings/{module}` |
| POST | `/api/admin/site-settings/{module}/reset` |

`module` = `chat` | `tts`。无需新接口。

## 3. 表单分组建议

### chat

1. **Agent**：`agent.enabled` / `max_steps` / `history_limit` / `l2_enabled`
2. **图片转述**：`image_caption.*`
3. **朗读分段**：`segmentation.*`（含 delay / fallback）
4. **附件**：`attachment_cache_ttl_minutes`

### tts

1. **总开关与语言**：`enabled` / `default_text_lang` / `default_prompt_lang`
2. **Seed 音色**：`seed_voice.enabled` / `seed_voice.name`
3. **超时与上传限制**：`connect_timeout_ms` / `read_timeout_ms` / `ref_audio.max_size_mb`

长文本：`image_caption.prompt` 用 TextArea。  
`danger` 字段（如 `agent.enabled`、`tts.enabled`）：保存前 Confirm。

## 4. 业务侧消费提示

| 设置 | 前端注意 |
| --- | --- |
| `chat.agent.enabled=false` | Agent 模式接口返回业务错误「Agent 模式暂未开放」；聊天 UI 可灰掉 Agent |
| `tts.enabled=false` | `GET /api/tts/config` 与 `/health` 含 `enabled=false`；合成/音色写接口返回「TTS 已关闭…」。入口可隐藏 |
| 密钥类 | 引导至「集成与密钥」页，避免两套表单 |

保存后**不必整站刷新**；下次请求生效。可选 toast：「已保存，对新请求生效」。

## 5. TTS config/health 增量字段

```json
{
  "enabled": true,
  "gptSovitsAvailable": true,
  "refPreloaded": false,
  "defaultVoiceId": 1,
  "defaultVoiceName": "达妮娅"
}
```

`health` 同样增加 `enabled`；关闭时 `available` 为 false，`message` 为「TTS 已在设置中关闭」。

## 6. Key 清单

完整契约见 [01-module-keys.md](./01-module-keys.md) 第 6～7 节。
