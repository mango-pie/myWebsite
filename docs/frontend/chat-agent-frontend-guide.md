# Chat Ask / Agent 双模式 — 前端对接指南

> 后端设计：[chat-agent-ask-mode-design.md](../design/chat-agent-ask-mode-design.md)  
> 会话模型（不变）：[chat-conversation-frontend-guide.md](./chat-conversation-frontend-guide.md)  
> Ask 模式 AstrBot SSE（不变）：[chat-astrbot-frontend-guide.md](./chat-astrbot-frontend-guide.md)

本文说明前端如何接入 **Ask（只读对话）** 与 **Agent（可执行本站业务）** 模式切换，以及如何消费扩展 SSE 事件。

---

## 1. 模式说明

| 模式 | `mode` 值 | 行为 | 分段 |
|------|-----------|------|------|
| **Ask**（默认） | `ask` 或省略 | AstrBot 角色聊天，无副作用 | 可能有 `segment_plan` |
| **Agent** | `agent` | 本地 Tool Loop，可创建待办/博客草稿/日记等 | **无** `segment_plan`，直接 `done` |

Agent 可用工具列表可通过 `GET /api/chat/agent/config` 动态获取。

---

## 2. UI 改造清单

### 2.1 模式切换控件

- 在聊天输入区上方增加 **Ask | Agent** Segmented Control（或 Toggle）。
- **默认 Ask**；建议 `localStorage.setItem('chatMode', 'ask'|'agent')` 记忆用户选择。
- 切换模式 **不清空** 当前 `conversationId`；下一条发送的消息起生效。

### 2.2 Agent 提示条

当 `mode === 'agent'` 时，输入框上方展示一行提示，文案可与 `/chat/agent/config` 的 `agentHint` 一致，例如：

> Agent 模式可创建待办、博客草稿、保存日记等；不会自动发布或删除。

### 2.3 Tool 卡片（Agent 专用）

在消息流中，**同一次 AI 回复** 建议结构为：

```
[Tool 卡片: list_study_lists ✓]
[Tool 卡片: create_study_task ✓]
[AI 文本气泡: 已为你创建今日待办…]
```

Tool 卡片在收到 `tool_call` 时显示 loading，收到对应 `tool_result` 后更新为成功/失败。

### 2.4 uiAction 联动

收到 `tool_result` 且带 `uiAction` 时，刷新或跳转对应模块（见 §6）。

---

## 3. 接口变更

### 3.1 POST `/api/chat/chat`（SSE）

**请求体**（在原有字段上扩展）：

```json
{
  "conversationId": 1001,
  "message": "帮我在今日待办里加一条：写博客大纲",
  "mode": "agent"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `conversationId` | 与 configId 二选一 | 同 v2 会话模型 |
| `configId` | 与 conversationId 二选一 | Ask 时决定 AstrBot 角色 |
| `message` | 是 | 用户文本；Agent 暂不支持 `segments` |
| `mode` | 否 | `ask`（默认）或 `agent` |
| `confirmToken` | 否 | L2 确认（二期，当前可忽略） |

**向后兼容**：不传 `mode` 时与现网完全一致（Ask + AstrBot + 可选 segment_plan）。

### 3.2 GET `/api/chat/agent/config`（新增）

```bash
curl -b cookies.txt 'http://localhost:8123/api/chat/agent/config'
```

响应示例：

```json
{
  "code": 0,
  "data": {
    "defaultMode": "ask",
    "agentEnabled": true,
    "agentHint": "Agent 模式可创建待办、博客草稿、保存日记等…",
    "modules": ["study", "blog", "diary"],
    "toolNames": [
      "list_study_lists",
      "list_today_tasks",
      "create_study_task",
      "toggle_study_task",
      "list_blog_categories",
      "list_blog_tags",
      "create_blog_draft",
      "update_blog_draft",
      "get_diary_by_date",
      "save_diary_entry"
    ]
  }
}
```

页面初始化时可拉取一次，用于展示 Agent 能力说明与设置页。

---

## 4. SSE 事件协议

每条 SSE 的 `data` 为 JSON 字符串。请先 `JSON.parse`，再按 `event` 分支。

### 4.1 事件一览

| event | 模式 | 说明 |
|-------|------|------|
| `chunk` | Ask / Agent | 流式文本；Ask 的 `type` 为 configId，Agent 为 `"agent"` |
| `segment_plan` | **仅 Ask** | 智能分段；Agent **不会出现** |
| `tool_call` | Agent | 开始调用工具 |
| `tool_result` | Agent | 工具执行结果，可含 `uiAction` |
| `error` | Agent | 可恢复错误（如步骤过多） |
| `done` | 全部 | 本轮结束 |

**向后兼容**：无 `event` 字段时按 `chunk` 处理（读 `d` 累积即可）。

### 4.2 Ask 模式（不变）

```
chunk → chunk → … → segment_plan? → done
```

详见 [chat-astrbot-frontend-guide.md](./chat-astrbot-frontend-guide.md) §4。

### 4.3 Agent 模式

```
tool_call → tool_result → … → chunk → done
```

无 `segment_plan`。最终 AI 气泡只由 `chunk` 累积；**不要**等待 segment_plan 再 finalize。

**tool_call 示例**

```json
{
  "event": "tool_call",
  "tool": "create_study_task",
  "args": { "title": "写博客大纲", "isToday": true },
  "step": 1,
  "type": "agent"
}
```

**tool_result 示例**

```json
{
  "event": "tool_result",
  "tool": "create_study_task",
  "success": true,
  "data": { "taskId": 1001, "title": "写博客大纲", "listId": 99 },
  "uiAction": { "type": "refresh", "module": "study_today" },
  "step": 1,
  "type": "agent"
}
```

**error 示例**

```json
{
  "event": "error",
  "message": "Agent 步骤过多，请拆分指令后重试",
  "type": "agent"
}
```

---

## 5. TypeScript 类型（建议）

```typescript
export type ChatMode = 'ask' | 'agent';

export interface ChatRequest {
  conversationId?: number;
  configId?: string;
  message: string;
  mode?: ChatMode;
  confirmToken?: string;
}

export interface AgentUiAction {
  type: 'refresh' | 'navigate' | 'toast';
  module?: string;
  path?: string;
  message?: string;
}

export interface ChatStreamEvent {
  event?: 'chunk' | 'segment_plan' | 'done' | 'tool_call' | 'tool_result' | 'error';
  d?: string;
  type?: string;
  segments?: string[];
  delays?: number[];
  tool?: string;
  args?: Record<string, unknown>;
  step?: number;
  success?: boolean;
  data?: Record<string, unknown>;
  uiAction?: AgentUiAction;
  message?: string;
}
```

---

## 6. uiAction 处理表

| uiAction.module | 建议行为 |
|-----------------|----------|
| `study_today` | 若在学习页，重新拉今日待办 API |
| `study_task_list` | 刷新任务列表视图 |
| `blog_list` | 刷新博客列表 |
| `blog_editor` | `router.push(uiAction.path)`，如 `/blog/edit/123` |
| `diary_day` | 刷新当前日记页 |

| uiAction.type | 说明 |
|---------------|------|
| `refresh` | 触发 `module` 对应数据 refetch |
| `navigate` | 跳转 `path` |
| `toast` | 显示 `message` |

实现建议：集中一个 `handleAgentUiAction(action: AgentUiAction)`，按 module 分发；新增后端 Tool 时，仅在有新 module 时需要补分支。

---

## 7. 消费 SSE 示例（Agent）

在现有 Ask 的 `fetch` + `ReadableStream` 基础上扩展 switch：

```typescript
async function sendChat(body: ChatRequest, onEvent: (ev: ChatStreamEvent) => void) {
  const res = await fetch('/api/chat/chat', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify(body),
  });
  if (!res.ok || !res.body) throw new Error(await res.text());

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let aiText = '';
  const toolSteps: ChatStreamEvent[] = [];

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';

    for (const line of lines) {
      if (!line.startsWith('data:')) continue;
      const raw = line.slice(5).trim();
      if (!raw) continue;
      const ev: ChatStreamEvent = JSON.parse(raw);
      const eventType = ev.event ?? 'chunk';
      onEvent(ev);

      switch (eventType) {
        case 'chunk':
          aiText += ev.d ?? '';
          break;
        case 'tool_call':
          toolSteps.push(ev);
          break;
        case 'tool_result':
          toolSteps.push(ev);
          if (ev.uiAction) handleAgentUiAction(ev.uiAction);
          break;
        case 'segment_plan':
          // Agent 模式不应出现；若 mode=ask 则走原有分段逻辑
          break;
        case 'done':
          finalizeAssistantMessage({ aiText, toolSteps, type: ev.type });
          break;
        case 'error':
          showError(ev.message);
          break;
      }
    }
  }
}
```

发送时：

```typescript
await sendChat({
  conversationId,
  message: input,
  mode: chatMode, // 'ask' | 'agent'
}, handleStreamEvent);
```

---

## 8. 组件状态建议

```typescript
interface AssistantTurn {
  role: 'assistant';
  mode: ChatMode;
  text: string;
  tools: Array<{
    tool: string;
    args?: Record<string, unknown>;
    success?: boolean;
    data?: Record<string, unknown>;
    status: 'running' | 'done' | 'error';
  }>;
  finalized: boolean;
}
```

- 收到 `tool_call`：push `{ tool, args, status: 'running' }`
- 收到 `tool_result`：按 `step` 或 `tool` 名匹配更新 `success` / `data` / `status`
- 收到 `chunk`：追加 `text`
- 收到 `done`：`finalized = true`（Agent **不要**再等 segment_plan）

---

## 9. 迁移检查清单

1. [ ] 聊天页增加 Ask / Agent 切换，默认 Ask
2. [ ] `POST /chat/chat` body 增加 `mode`
3. [ ] SSE 解析增加 `tool_call` / `tool_result` / `error` 分支
4. [ ] Agent 回合渲染 Tool 卡片 + 最终文本气泡
5. [ ] 实现 `handleAgentUiAction`，至少覆盖 `study_today`、`blog_editor`、`diary_day`
6. [ ] Agent 模式下 **禁用** segment_plan 气泡拆分逻辑
7. [ ] （可选）启动时 `GET /chat/agent/config` 展示能力与开关
8. [ ] 会话 resolve / 历史加载逻辑 **无需改动**（仍用 conversationId）

---

## 10. 验收用例

| # | mode | 用户输入 | 期望 |
|---|------|----------|------|
| 1 | ask | 「你好」 | 仅 chunk + done，无 tool_* |
| 2 | agent | 「列出我的学习清单」 | tool_call + tool_result + chunk + done |
| 3 | agent | 「今日待办加一条：复习 Redis」 | create_study_task + uiAction refresh study_today |
| 4 | agent | 「写一篇标题为 XX 的博客草稿」 | create_blog_draft + navigate blog_editor |
| 5 | 省略 mode | 任意 | 与现网 Ask 一致 |
| 6 | agent | 非 administrator 调 Study | tool_result success=false，文本说明无权限 |

---

## 11. 后端新增 Tool 时前端是否要改？

| 变更 | 前端工作 |
|------|----------|
| 仅新增 Tool，复用已有 `uiAction.module` | **通常不用改** |
| 新增 `uiAction.module` 值 | 在 `handleAgentUiAction` 增加一条 refresh/navigate |
| 新增 L2 确认流（二期） | 增加 Confirm 卡片 + `confirmToken` 回传 |

工具名列表以 `GET /chat/agent/config` 的 `toolNames` 为准，可用于设置页「Agent 能做什么」说明，不必硬编码。

---

## 12. 参考

- 设计文档：[chat-agent-ask-mode-design.md](../design/chat-agent-ask-mode-design.md)
- 后端入口：[ChatController.java](../../src/main/java/com/ai/controller/ChatController.java)、[ChatOrchestrator.java](../../src/main/java/com/ai/agent/ChatOrchestrator.java)
- 扩展 Tool：新增 `com.ai.agent.tools.*AgentToolModule` 即可自动注册
