# AstrBot 聊天集成 - 前端修改指南

> 后端聊天能力已全面迁移至 [AstrBot](https://docs.astrbot.app/dev/openapi.html) 平台预设（BFF 代理模式）。本地系统提示词角色（PromptTypeEnum + 6 个 prompt txt）已废弃。网页代码生成（HTML / 多文件模式）**保持原有 LangChain4j 设计不变**。
;
本文档面向前端开发者，说明接口变更、如何消费 SSE、角色切换、以及为图片/语音/富消息预留的扩展点。

---

## 1. 为什么改？

- **GET + querystring 传 message 长度受限**：浏览器/Nginx 通常 2k~8k，中文编码后膨胀，粘贴代码/长需求易失败。
- **安全**：AstrBot API Key 仅后端持有（BFF），前端不再需要知道后端 AI 地址/密钥。
- **可扩展**：支持 AstrBot 的消息段（message chain：plain + image/record/file + attachment_id），为聊天内图片、语音附件、引用回复预留。
- **统一角色管理**：角色/人格/Agent 能力全部在 AstrBot WebUI 配置（支持插件、Tools、MCP），前端通过 `/configs` 拉取列表。

响应格式与旧 SSE 保持兼容：`data: {"event":"chunk","d":"chunk","type":"..."}`（type 现在是 configId 或 "astrbot"）。流结束后可能追加 `segment_plan` 与 `done` 事件，用于智能分段展示（见第 4 节）。

---

## 2. 新接口总览

| 方法 | 路径 | 说明 | 变更 |
|------|------|------|------|
| POST | `/api/chat/conversations/resolve` | 获取/创建角色默认会话 | **v2 新增** |
| POST | `/api/chat/conversations` | 新建非默认会话 | **v2 新增** |
| GET  | `/api/chat/conversations` | 会话列表 | **v2 新增** |
| GET  | `/api/chat/conversations/{id}/messages` | 角色聊天历史 | **v2 新增，替代 chatHistory+appId** |
| POST | `/api/chat/chat` | 发送聊天消息（SSE 返回） | **v2：用 conversationId，不再用 appId** |
| GET  | `/api/chat/configs` | 获取 AstrBot 可用预设列表（id + name） | 原来是 `/types` 返回本地枚举 |
| GET  | `/api/chat/types` | 已废弃（返回空数组） | 仅为兼容，尽快迁移 |

代码生成接口 `/api/app/chat/gen/code` **不变**（仍 GET + `appId` + message，内部用 codegen prompt + LangChain4j）。

> **会话模型 v2 完整迁移说明**见 [chat-conversation-frontend-guide.md](./chat-conversation-frontend-guide.md)。

---

## 3. 请求示例

```bash
# 1. 先获取角色列表
curl 'http://localhost:8123/api/chat/configs'

# 2. 获取或创建该角色的默认会话（v2，替代 POST /app/add）
curl -X POST 'http://localhost:8123/api/chat/conversations/resolve' \
  -H 'Content-Type: application/json' \
  -d '{"configId":"dania"}'
# 记下返回的 data.id 作为 conversationId

# 3. 发送聊天（长文本 OK）
curl -N 'http://localhost:8123/api/chat/chat' \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": 1001,
    "message": "你好，请用达妮娅的语气介绍一下自己。以下是长文本需求：\n\n..."
  }'
```

响应流（SSE）示例：

```
data: {"event":"chunk","d":"你","type":"dania"}

data: {"event":"chunk","d":"好","type":"dania"}

data: {"event":"segment_plan","segments":["你好呀","今天怎么样？"],"delays":[0,420],"type":"dania"}

data: {"event":"done","type":"dania"}
```

- `chunk`：流式打字，与旧版 `d` 字段行为一致
- `segment_plan`（可选）：流结束后智能分段成功时下发；前端应将最后一条 AI 气泡**替换**为 `segments[0]`，再按 `delays[i]` 依次追加后续段
- `done`：本轮结束；也可在收到 `done` 或连接关闭时 finalize
- **向后兼容**：若 payload 无 `event` 字段，按 `chunk` 处理（读 `d` 累积即可）

流结束后连接关闭。若未启用分段或分段失败，只会收到 `chunk` 序列 + `done`，不会出现 `segment_plan`。

---

## 4. 前端消费 POST SSE（关键代码）

`EventSource` 仅支持 GET，因此必须用 `fetch` + `ReadableStream`。

### SSE 事件类型

| event | 字段 | 前端动作 |
|-------|------|----------|
| `chunk` | `d`, `type` | 追加到最后一条 AI 气泡（打字效果） |
| `segment_plan` | `segments[]`, `delays[]`, `type` | 用 `segments[0]` **替换**最后一条 AI 气泡；再按 `delays[i]` 延迟 append `segments[1..]` |
| `done` | `type` | 本轮结束，可 finalize / 触发 TTS |
| （无 event） | `d`, `type` | 兼容旧版，等同 `chunk` |

```ts
// chatApi.ts
export type ChatSsePayload = {
  event?: 'chunk' | 'segment_plan' | 'done';
  d?: string;
  type?: string;
  segments?: string[];
  delays?: number[];
};

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

export async function* streamChat(
  conversationId: number,
  message: string,
  signal?: AbortSignal
): AsyncGenerator<ChatSsePayload> {
  const res = await fetch('/api/chat/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ conversationId, message }),
    signal,
  });

  if (!res.ok || !res.body) {
    const text = await res.text().catch(() => '');
    throw new Error(`Chat failed: ${res.status} ${text}`);
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    let idx: number;
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim();
      buffer = buffer.slice(idx + 1);

      if (line.startsWith('data:')) {
        const jsonStr = line.slice(5).trim();
        if (jsonStr) {
          try {
            yield JSON.parse(jsonStr) as ChatSsePayload;
          } catch {
            yield { event: 'chunk', d: jsonStr };
          }
        }
      }
    }
  }
}

// 使用示例（React + 智能分段）
async function send() {
  let full = '';
  for await (const payload of streamChat(conversationId, input)) {
    const event = payload.event ?? (payload.d ? 'chunk' : undefined);

    if (event === 'chunk' && payload.d) {
      full += payload.d;
      setMessages(prev => {
        const copy = [...prev];
        copy[copy.length - 1] = full;
        return copy;
      });
    } else if (event === 'segment_plan' && payload.segments?.length) {
      replaceLastAiBubble(payload.segments[0]);
      for (let i = 1; i < payload.segments.length; i++) {
        await sleep(payload.delays?.[i] ?? 350);
        appendAiBubble(payload.segments[i]);
      }
      full = payload.segments.join('');
    } else if (event === 'done') {
      // 可用于 TTS：full 或 segments 拼接后的完整文本
    }
  }
}
```

注意事项：
- 按 `\n` 切分即可，每条 `data:` 通常一行 JSON。
- `segment_plan` 可能在最后一个 `chunk` 之后、`done` 之前到达；收到后应替换而非追加到累积文本。
- 错误时后端会走 doOnError 写 "AI回复失败: xxx" 到历史，同时流会以异常结束，前端 catch 即可显示。
- 建议带 AbortSignal，支持用户取消。
- 后端可通过 `chat.segmentation.enabled=false` 关闭分段，此时只有 `chunk` + `done`。

---

## 5. 角色（预设）切换

```ts
// 加载 AstrBot 预设
const [roles, setRoles] = useState<{id: string, name: string}[]>([]);
useEffect(() => {
  fetch('/api/chat/configs')
    .then(r => r.json())
    .then(data => setRoles(data.data || []))
    .catch(console.error);
}, []);

// 进入角色页时 resolve 默认会话
const conv = await resolveConversation(selectedRoleId);
// 发送时
await sendMessage({ conversationId: conv.id, message });
```

- 每个 `conversationId` 对应稳定 AstrBot session，同角色默认会话自动续聊。
- 不同 `configId` / `conversationId` 上下文隔离。
- type 字段会带上 configId，方便前端按角色区分气泡样式或头像。
- 如果 AstrBot WebUI 里改了预设名字/增删，刷新 /configs 即可，前端无需硬编码。

---

## 6. 图片 / 附件（当前 + 未来）

当前可用方案（展示用）：
1. 用户选择图片 -> `POST /api/upload/common`（已有）返回 url
2. 把 url 展示在聊天输入区或历史
3. 文本消息里写 "请看这张图：![alt](url)" 或单独发

未来 AstrBot 原生附件支持（预留）：
- 新接口 `POST /api/chat/attachment`（multipart），后端先存本地（复用 ImageUploadService），再转发给 AstrBot `/api/v1/file` 换取 attachment_id
- 返回 `{ localUrl, attachmentId }`
- 聊天时用 segments：
  ```json
  {
    "conversationId": 1001,
    "configId": "vision",
    "segments": [
      { "type": "plain", "text": "分析这张图" },
      { "type": "image", "attachmentId": "9a2f8c72-..." }
    ]
  }
  ```
- ChatRequest 已包含 `segments?: ChatMessageSegment[]`，后端 DTO 就绪，待 service/facade 扩展转发。

---

## 7. 语音文字互转（当前 + 未来）

**TTS（文字转语音，输出）**：
- 流结束后拿到完整 `fullText`
- 直接复用现有 `POST /api/tts/synthesize`（返回 audio/wav）
- 注意：当前该接口要求 `@AuthCheck(mustRole = ADMINISTRATOR_ROLE)`，生产环境建议：
  - 放宽为登录用户
  - 或新增 `/api/tts/speak` 轻量包装（内部调 synthesize）
- 可在 SSE 结束后立即播放，或提供“朗读”按钮。

**STT（语音转文字，输入）**：
- 浏览器 `MediaRecorder` 录制 -> blob
- 未来 `POST /api/chat/transcribe`（multipart audio）返回 `{ text: "识别结果" }`
- 然后把 text 作为 message 正常发 chat
- 后端预留 `SttService` 接口（当前 stub），可插真实 Whisper / 硅基流动 / 其他。

语音往返示例流程：
录制 -> (STT) -> 文本发 chat -> 流式显示 -> (可选自动/按钮) TTS 播放完整回复

---

## 8. 错误处理 & 边界

- 参数错误：`conversationId` 与 `configId` 均未传 / 消息为空 -> 400 BusinessException
- 会话不存在或无权访问 -> 404 / 403
- AstrBot 不可达或超时 -> 流 onError "AI回复失败: AstrBot 服务不可用..." ，同时后端已落库错误消息
- 超长 Agent 思考：read-timeout 默认 5 分钟，可在 yml 调大
- 重连：建议在组件 unmount 时 abort，重新发送时可带历史上下文提示（或靠 AstrBot session 自动带）

---

## 9. 会话模型迁移（v2）

角色聊天已与代码生成 **彻底分离**，详见 [chat-conversation-frontend-guide.md](./chat-conversation-frontend-guide.md)。

要点：
1. 执行 `src/main/resources/sql/chat_conversation_schema.sql`
2. 删除角色聊天中的 `POST /app/add` 与 `appId`
3. 使用 `POST /chat/conversations/resolve` + `conversationId`
4. 历史改用 `GET /chat/conversations/{id}/messages`
5. 代码生成仍用 `appId` + `/chatHistory/list?appId=`

## 10. 迁移检查清单（从旧本地角色切到 AstrBot）

1. 搜索项目中所有 `GET.*chat/chat` 或 `fetch.*chat\?appId`，全部改为 POST + JSON body。
2. 角色列表加载：把 `fetch('/api/chat/types')` 及 `PromptTypeEnum` 枚举渲染，改成 `fetch('/api/chat/configs')` + 用返回的 id/name 渲染下拉/Tab。
3. 选角色后 `resolve` 会话，发消息带 `conversationId`（或仅 `configId` 自动 resolve）。
4. 移除或注释掉前端硬编码的本地角色列表（"茜"、"达妮娅"、"Python 导师" 等）。
5. 测试：
   - 同角色退出再进，历史连续
   - 超长中文消息（> 2000 字）
   - 不同 configId / conversationId 上下文隔离
   - 图片上传 + 展示（当前 /upload/common）
   - 流中断 + 错误提示
   - 智能分段：`segment_plan` 事件替换气泡 + 延迟补发后续段
6. 代码生成相关 UI（创建 App、chatToGenCode）无需改动。
7. 后端部署时确保：
   - `application.yml` astrbot.api-key 已替换为真实 key（scope 至少含 chat + config）
   - AstrBot 服务在 121.40.253.15:6185 可从后端访问
   - （可选）加启动日志探测 AstrBot 是否可用

---

## 11. 后端配置参考（给运维/后端看）

```yaml
astrbot:
  base-url: http://121.40.253.15:6185
  api-key: abk_你的真实key
  connect-timeout-ms: 5000
  read-timeout-ms: 300000   # Agent 工具调用可能较慢

# 聊天智能分段（流结束后 LLM 分段，SSE segment_plan 通知前端）
chat:
  segmentation:
    enabled: true
    style: natural          # natural | conservative | active
    min-length: 15
    max-segments: 8
    timeout-seconds: 5      # 超时后走标点规则兜底
    delay-base: 0.35
    delay-per-char: 0.015
    delay-max: 1.2

langchain4j:
  open-ai:
    streaming-chat-model:   # 代码生成（本地代理）
      base-url: http://127.0.0.1:14514/v1
      model-name: deepseek-v4-flash
    chat-model:             # 智能分段专用（阿里百炼 qwen-flash，与主聊天/代码生成分离）
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${DASHSCOPE_API_KEY}   # 百炼控制台 API Key，或写入环境变量
      model-name: qwen-flash
      max-tokens: 256
      temperature: 0.1
      timeout: 5s
```

健康检查（管理员）可扩展 `GET /api/chat/health` 返回 available。

---

## 12. 未来演进（不阻塞当前）

- 支持 segments 完整转发 + AstrBot `/api/v1/file` 上传
- 引入 SttService 实现语音输入
- 放松 TTS 权限或加 `/speak` 端点
- 多引擎工厂（AstrBot + 本地回退）
- 聊天历史富化（metadata 存 segments/attachment）
- 流中携带 toolCall / audioHint 等扩展字段（type 里带）

---

参考：
- AstrBot HTTP API: https://docs.astrbot.app/dev/openapi.html
- 后端实现：`AstrBotChatServiceImpl`、`AiChatFacade`、`ChatController`、`ChatSegmentationServiceImpl`
- 类似 BFF 模式可参考项目内 `frontend-tts-api.md` + `TtsProxyServiceImpl`

有问题优先看后端日志（AstrBot 返回的原始 SSE event 会在解析前被容错处理，可临时加 log 看 raw data）。迁移顺利！