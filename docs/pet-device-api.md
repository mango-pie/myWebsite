# EchoBot / 桌宠云端桥接

本地 EchoBot（`E:\EchoBot`）通过长期 **Device Token** 调用网站 `/api/pet/*`，网站部署在 Linux 云服务器。

## 鉴权

1. 用户在网站登录（Session Cookie）
2. `POST /api/pet/devices` 绑定设备 → **明文 token 只返回一次**
3. EchoBot 配置：
   ```env
   SITE_API_BASE_URL=https://你的域名/api
   SITE_DEVICE_TOKEN=pet_xxxx
   ```
4. 请求头：`Authorization: Bearer pet_xxxx`

库表：`pet_device`（Flyway `V17`）、`worklog_entry`（Flyway `V18`）

## 接口一览

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/pet/me` | Session 或 Token | 当前用户 |
| POST | `/pet/devices` | **仅 Session** | 绑定，返回 token |
| GET | `/pet/devices` | 仅 Session | 设备列表 |
| POST | `/pet/devices/revoke` | 仅 Session | `{ "id": deviceId }` |
| GET | `/pet/chat/configs` | Token | AstrBot 角色列表 |
| POST | `/pet/chat/conversations/resolve` | Token | `{ "configId" }` |
| POST | `/pet/chat` | Token | SSE 聊天（默认 ask） |
| GET | `/pet/chat/conversations/{id}/messages` | Token | 历史消息 |
| GET | `/pet/diary/today` | Token | 今日日记 |
| GET | `/pet/diary/by-date?date=` | Token | 按日日记 |
| POST | `/pet/diary/save` | Token | 保存日记 |
| GET | `/pet/diary/month` | Token | 月历摘要 |
| GET | `/pet/worklog/today` | Token | 今日工作日志 |
| GET | `/pet/worklog/by-date` | Token | 按日工作日志 |
| GET | `/pet/worklog/list` | Token | 最近列表 |
| POST | `/pet/worklog/save` | Token | 保存工作日志 |
| POST | `/pet/worklog/delete` | Token | `{ "date" }` |
| GET | `/pet/tts/voices` | Token | 音色列表 |
| POST | `/pet/tts/synthesize` | Token | 返回 WAV（用户级，非 admin） |

网页工作日志：`/api/worklog/*`（Session），模块开关 `app.modules.worklog`。

## EchoBot Tools

配置 `SITE_*` 后自动注册：`site_me` / `site_diary` / `site_worklog` / `site_chat` / `site_tts`
