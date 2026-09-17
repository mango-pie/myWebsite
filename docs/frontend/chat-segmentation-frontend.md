# 聊天智能分段 — 前端对接说明

后端已在 `POST /api/chat/chat` 的 SSE 流中支持智能分段。SSE 解析与消息列表渲染逻辑需调整；请求体已改为 `conversationId`（见 [chat-conversation-frontend-guide.md](./chat-conversation-frontend-guide.md)），不再使用 `appId`。

---

## 1. 发生了什么

一轮对话的 SSE 顺序通常是：

```
chunk → chunk → … → segment_plan（可选）→ done
```

| 阶段 | 用户看到的效果 |
|------|----------------|
| `chunk` | 最后一条 AI 气泡逐字变长（与原来一样） |
| `segment_plan` | 把这条气泡**拆成多条**，后续段带延迟逐条弹出 |
| `done` | 本轮结束，可触发 TTS 等 |

若后端未分段（短回复、分段失败、或 `enabled=false`），则只有 `chunk` + `done`，没有 `segment_plan`。

---

## 2. SSE 载荷格式

```ts
type ChatSsePayload = {
  event?: 'chunk' | 'segment_plan' | 'done';
  d?: string;              // chunk 文本片段
  type?: string;           // configId，如 "dania"
  segments?: string[];     // segment_plan 时的分段结果
  delays?: number[];       // 每段延迟（毫秒），delays[0] 恒为 0
};
```

示例：

```json
{"event":"chunk","d":"你好","type":"dania"}
{"event":"segment_plan","segments":["你好呀","今天怎么样？"],"delays":[0,420],"type":"dania"}
{"event":"done","type":"dania"}
```

**兼容旧代码**：没有 `event` 字段时，有 `d` 就按 `chunk` 处理。

---

## 3. 前端必改的三处

### ① 解析 SSE 时保留完整 payload

不要只 yield `d`，要把 `event`、`segments`、`delays` 一并传出。

### ② 处理 `chunk`（不变）

```ts
if (event === 'chunk' && payload.d) {
  appendToLastAiBubble(payload.d);  // 累积到最后一条 AI 消息
}
```

### ③ 处理 `segment_plan`（新增）

```ts
if (event === 'segment_plan' && payload.segments?.length) {
  replaceLastAiBubble(payload.segments[0]);           // 替换，不是追加
  for (let i = 1; i < payload.segments.length; i++) {
    await sleep(payload.delays?.[i] ?? 350);
    appendAiBubble(payload.segments[i]);              // 新建一条 AI 气泡
  }
}
```

### ④ 处理 `done`（建议新增）

```ts
if (event === 'done') {
  finalize();  // 结束 loading、允许 TTS 等
}
```

---

## 4. 完整消费示例

```ts
const sleep = (ms: number) => new Promise(r => setTimeout(r, ms));

async function sendMessage(appId: number, message: string, configId?: string) {
  appendAiBubble(''); // 先插入 AI 占位气泡

  const res = await fetch('/api/chat/chat', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ appId, configId, message }),
  });

  const reader = res.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let fullText = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    let idx: number;
    while ((idx = buffer.indexOf('\n')) >= 0) {
      const line = buffer.slice(0, idx).trim();
      buffer = buffer.slice(idx + 1);
      if (!line.startsWith('data:')) continue;

      const payload = JSON.parse(line.slice(5).trim());
      const event = payload.event ?? (payload.d ? 'chunk' : undefined);

      if (event === 'chunk' && payload.d) {
        fullText += payload.d;
        replaceLastAiBubble(fullText);
      } else if (event === 'segment_plan' && payload.segments?.length) {
        replaceLastAiBubble(payload.segments[0]);
        for (let i = 1; i < payload.segments.length; i++) {
          await sleep(payload.delays?.[i] ?? 350);
          appendAiBubble(payload.segments[i]);
        }
        fullText = payload.segments.join('');
      } else if (event === 'done') {
        // 可选：TTS 用 fullText
      }
    }
  }
}
```

---

## 5. 注意事项

1. **`segment_plan` 是替换，不是追加** — 流式阶段已经显示过整段，收到分段计划后要用 `segments[0]` 覆盖最后一条气泡。
2. **`delays[i]` 单位是毫秒** — 控制第 2、3… 条气泡弹出的间隔，模拟真人连发。
3. **历史记录** — 后端仍存整段原文；前端展示可以有多条气泡，刷新后若只读历史接口，可能仍是一条（除非后续历史接口也支持 segments）。
4. **TTS** — 建议在 `done` 或 `segment_plan` 之后，用 `segments.join('')` 或流式累积的 `fullText` 朗读。
5. **关闭分段** — 后端设 `chat.segmentation.enabled=false` 时，前端无需改动，只是永远收不到 `segment_plan`。

---

## 6. 自测清单

- [ ] 长回复能收到 `segment_plan`，气泡拆成多条且有延迟
- [ ] 短回复（<15 字）只有 `chunk` + `done`
- [ ] 旧逻辑（只读 `d`）仍能打字，只是不会分段
- [ ] 用户中途 abort 不会报错

## 7. 收不到 `segment_plan`？

日志里若只有 `chunk: 1, segment_plan: 0, done: 1`，说明**后端认为不需要或未成功拆条**，常见原因：

| 原因 | 说明 |
|------|------|
| 回复太短 | 少于 15 字（`chat.segmentation.min-length`）不会分段 |
| 无法拆成多条 | 整段无句号/问号/感叹号等，LLM 与规则兜底都只有 1 段 |
| 分段 LLM 不可用 | 默认连 `http://127.0.0.1:14514/v1`；失败时会自动尝试**标点规则兜底**（需文中有 。！？ 等） |
| 功能已关闭 | `chat.segmentation.enabled=false` |

**排查**：看后端日志是否有 `下发 segment_plan: N 段` 或 `未下发 segment_plan: 分段结果仍为单段`。

确保 `langchain4j.open-ai.chat-model` 的 `base-url` 可访问；或依赖规则兜底（回复需含中文句读标点）。

更完整的 AstrBot 聊天迁移说明见 [chat-astrbot-frontend-guide.md](./chat-astrbot-frontend-guide.md)。
