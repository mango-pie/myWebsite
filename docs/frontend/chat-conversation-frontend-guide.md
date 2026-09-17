# 角色聊天会话模型 — 前端迁移指南（v2）

> **破坏性变更**：角色聊天已与代码生成 App **彻底分离**。不再使用 `appId` / `POST /app/add` 创建聊天会话。  
> 代码生成仍使用 `appId` + `/api/app/chat/gen/code` + `/api/chatHistory/*?appId=`，**不受影响**。

完整 AstrBot SSE、分段说明见 [chat-astrbot-frontend-guide.md](./chat-astrbot-frontend-guide.md)。

---

## 1. 为什么要改？

| 旧做法 | 问题 |
|--------|------|
| 选角色 → `POST /app/add` → 拿 `appId` | 每次进聊天都新建 App，历史与 AstrBot 上下文断裂 |
| 历史走 `GET /chatHistory/list?appId=` | 与代码生成历史混在同一张表，语义混乱 |

| 新做法 | 收益 |
|--------|------|
| 选角色 → `POST /chat/conversations/resolve` | 同角色自动续上默认会话 |
| 历史走 `GET /chat/conversations/{id}/messages` | 角色聊天独立存储，支持同角色多会话 |

---

## 2. 部署前：执行数据库脚本

在 MySQL 中执行：

```bash
# 文件路径
src/main/resources/sql/chat_conversation_schema.sql
```

将创建 `chat_conversation`、`chat_message` 两张表。`chat_history` **不改动**，继续服务代码生成。

---

## 3. 新接口总览

Base URL 已含 `context-path: /api`。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/conversations/resolve` | 获取/创建该角色**默认会话** |
| POST | `/chat/conversations` | **新建**非默认会话（同角色多对话） |
| GET | `/chat/conversations` | 会话列表，`?configId=` 可选过滤 |
| GET | `/chat/conversations/{id}` | 会话详情 |
| GET | `/chat/conversations/{id}/messages` | 分页消息历史 |
| DELETE | `/chat/conversations/{id}` | 软删会话 |
| POST | `/chat/chat` | 发消息（SSE），见下文 |
| GET | `/chat/configs` | 角色列表（不变） |

---

## 4. 推荐用户流程

```
进入角色页
  → POST /chat/conversations/resolve { configId }
  → 保存 conversationId
  → GET /chat/conversations/{id}/messages 拉历史
  → POST /chat/chat { conversationId, message }

侧边栏「该角色的所有对话」
  → GET /chat/conversations?configId=dania

用户点「新对话」
  → POST /chat/conversations { configId, title? }
  → 切换到新 conversationId

发消息（便捷写法，未持有 conversationId 时）
  → POST /chat/chat { configId, message }  // 后端自动 resolve 默认会话
```

---

## 5. 请求/响应示例

### 5.1 resolve 默认会话

```bash
curl -X POST 'http://localhost:8123/api/chat/conversations/resolve' \
  -H 'Content-Type: application/json' \
  -H 'Cookie: JSESSIONID=...' \
  -d '{"configId":"dania"}'
```

响应：

```json
{
  "code": 0,
  "data": {
    "id": 1001,
    "configId": "dania",
    "configName": "达妮娅",
    "title": "与 达妮娅 的对话",
    "isDefault": true,
    "lastMessageAt": "2026-06-06T10:30:00",
    "createTime": "2026-06-01T08:00:00",
    "messageCount": 12
  }
}
```

### 5.2 新建会话

```bash
curl -X POST 'http://localhost:8123/api/chat/conversations' \
  -H 'Content-Type: application/json' \
  -d '{"configId":"dania","title":"周末聊的话题"}'
```

`isDefault` 为 `false`。

### 5.3 发消息（SSE）

```bash
curl -N 'http://localhost:8123/api/chat/chat' \
  -H 'Content-Type: application/json' \
  -d '{
    "conversationId": 1001,
    "message": "你好呀"
  }'
```

也可只传 `configId`（自动 resolve）：

```json
{
  "configId": "dania",
  "message": "你好呀"
}
```

**不再接受 `appId`。**

SSE 格式不变：`chunk` → `segment_plan`（可选）→ `done`。

### 5.4 拉历史

```bash
curl 'http://localhost:8123/api/chat/conversations/1001/messages?pageNum=1&pageSize=20'
```

消息字段：

| 字段 | 说明 |
|------|------|
| `messageType` | `user` / `ai` / `error` |
| `content` | 正文 |
| `source` | `normal` / `proactive`（预留主动消息） |
| `createTime` | 时间 |

---

## 6. TypeScript 参考实现

```ts
export type ChatConversation = {
  id: number;
  configId: string;
  configName: string;
  title: string;
  isDefault: boolean;
  lastMessageAt?: string;
  createTime: string;
  messageCount?: number;
};

export type ChatMessage = {
  id: string;
  conversationId: number;
  messageType: 'user' | 'ai' | 'error';
  content: string;
  source: 'normal' | 'proactive';
  createTime: string;
};

/** 进入角色页：获取或创建默认会话 */
export async function resolveConversation(configId: string): Promise<ChatConversation> {
  const res = await fetch('/api/chat/conversations/resolve', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ configId }),
  });
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

/** 新建非默认会话 */
export async function createConversation(configId: string, title?: string): Promise<ChatConversation> {
  const res = await fetch('/api/chat/conversations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: JSON.stringify({ configId, title }),
  });
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

/** 列出会话（可按角色过滤） */
export async function listConversations(configId?: string): Promise<ChatConversation[]> {
  const qs = configId ? `?configId=${encodeURIComponent(configId)}` : '';
  const res = await fetch(`/api/chat/conversations${qs}`, { credentials: 'include' });
  const json = await res.json();
  if (json.code !== 0) throw new Error(json.message);
  return json.data;
}

/** 流式发消息 — 使用 conversationId */
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
  if (!res.ok || !res.body) throw new Error(`chat failed: ${res.status}`);
  // ... 与 chat-astrbot-frontend-guide.md 中 SSE 解析相同
  yield* parseSseStream(res.body);
}
```

---

## 7. 破坏性变更清单

| 移除 / 停用 | 替换为 |
|-------------|--------|
| 聊天前 `POST /app/add` | `POST /chat/conversations/resolve` |
| `ChatRequest.appId` | `conversationId` 或 `configId` |
| `GET /chatHistory/list?appId=`（角色聊天） | `GET /chat/conversations/{id}/messages` |
| `GET /chatHistory/...` 保存用户/AI 消息（角色聊天） | 由 `POST /chat/chat` 自动落库 |

**保留不变（代码生成）**：

- `POST /app/add`、`GET /app/chat/gen/code?appId=&message=`
- `GET /chatHistory/list?appId=` 在代码生成页继续使用

---

## 8. 迁移检查清单

1. [ ] 执行 `chat_conversation_schema.sql`
2. [ ] 删除角色聊天流程中所有 `POST /app/add`
3. [ ] 全局搜索聊天相关 `appId`，改为 `conversationId`
4. [ ] 角色入口调用 `resolveConversation(configId)`
5. [ ] 历史加载改为 `/chat/conversations/{id}/messages`
6. [ ] 可选：侧边栏展示 `listConversations(configId)` +「新对话」按钮
7. [ ] 确认代码生成页仍用 `appId`，未误改
8. [ ] 测试：同角色退出再进 → 历史连续；新建会话 → 上下文隔离

---

## 9. 后端实现索引

- SQL：[src/main/resources/sql/chat_conversation_schema.sql](../../src/main/resources/sql/chat_conversation_schema.sql)
- 会话 API：[ChatConversationController](../../src/main/java/com/ai/controller/ChatConversationController.java)
- 聊天入口：[AiChatFacade](../../src/main/java/com/ai/core/AiChatFacade.java)
- AstrBot session：`chat_user_{userId}_cfg_{configId}_conv_{conversationId}`
