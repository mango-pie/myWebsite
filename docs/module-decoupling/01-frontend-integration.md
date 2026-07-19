# 模块解耦 · 前端对接文档

配合后端 Phase 1–3 的「模块开关 + 条件装配 + SPI 解耦」。后端已支持通过 `app.modules.*` 关闭任一业务模块（关闭后其 API 直接 404、Bean/Mapper 不注册）。本文说明前端需要配套做的工作。

- 后端 base path：`/api`（`server.servlet.context-path`），默认端口 `8123`。
- 模块 key：`ops` / `blog` / `knowledge` / `reading` / `chat` / `study` / `diary` / `tts` / `app-lab`。

---

## 1. 能力探测接口

### `GET /api/app/modules`

无需鉴权即可调用（供首屏渲染）。返回各业务模块是否启用。

**响应示例**

```json
{
  "code": 0,
  "data": {
    "modules": {
      "ops": true,
      "blog": true,
      "knowledge": true,
      "reading": true,
      "chat": true,
      "study": true,
      "diary": true,
      "tts": true,
      "app-lab": true
    }
  },
  "message": "ok"
}
```

> `modules` 为有序 Map；新增模块时后端会追加 key，前端应做「缺省即视为关闭」的容错，不要假设 key 一定存在。

---

## 2. 启动时拉取并全局缓存

应用初始化（首屏或登录后）调用一次，存入全局 store，全站据此渲染。

```ts
// stores/capabilities.ts（Pinia 示例）
import { defineStore } from 'pinia'

export const useCapabilities = defineStore('capabilities', {
  state: () => ({ modules: {} as Record<string, boolean>, loaded: false }),
  getters: {
    // 缺省视为关闭，避免误显示
    enabled: (s) => (name: string) => s.modules[name] === true,
  },
  actions: {
    async load() {
      const { data } = await http.get('/app/modules')
      this.modules = data?.data?.modules ?? {}
      this.loaded = true
    },
  },
})
```

```ts
// 入口处
await useCapabilities().load()
```

---

## 3. 菜单 / 路由按能力隐藏

### 3.1 模块 → 前端入口映射

| 模块 key | 前端入口（菜单/路由） |
| --- | --- |
| `blog` | 博客管理 |
| `knowledge` | 精读工作台 / 知识库 |
| `chat` | 对话 |
| `study` | 学习 / 任务 |
| `diary` | 日记 |
| `tts` | 语音合成 |
| `ops` | 运维 / 审计 / 用量统计看板 |
| `app-lab` | 应用（代码）生成实验台 |
| `reading` | 一般无需独立入口（精读能力归 `knowledge`，`reading` 仅控制异步任务队列）；仅当有「任务队列」页时才隐藏 |

### 3.2 菜单过滤

给菜单项加 `requireModule` 元信息，渲染时过滤：

```ts
const menus = [
  { title: '对话', path: '/chat', requireModule: 'chat' },
  { title: '精读工作台', path: '/knowledge', requireModule: 'knowledge' },
  { title: '博客管理', path: '/blog', requireModule: 'blog' },
  { title: '学习', path: '/study', requireModule: 'study' },
  { title: '日记', path: '/diary', requireModule: 'diary' },
  { title: '语音合成', path: '/tts', requireModule: 'tts' },
  { title: '运维看板', path: '/ops', requireModule: 'ops' },
  { title: '应用实验台', path: '/app-lab', requireModule: 'app-lab' },
  // 无 requireModule 的为 platform 常驻（用户、站点设置等）
]

const caps = useCapabilities()
const visibleMenus = menus.filter(m => !m.requireModule || caps.enabled(m.requireModule))
```

### 3.3 路由守卫

被隐藏的模块即使被直接输入 URL 也应拦截：

```ts
router.beforeEach((to) => {
  const need = to.meta?.requireModule as string | undefined
  const caps = useCapabilities()
  if (need && caps.loaded && !caps.enabled(need)) {
    return { path: '/404' } // 或首页
  }
})
```

---

## 4. 跨模块按钮的条件显示（重点）

即使某模块入口保留，下列**依赖其它模块**的操作，必须按被依赖模块的开关显示，否则点击会收到明确错误：

| 操作位置 | 依赖模块 | 关闭时后端行为 |
| --- | --- | --- |
| 精读详情：**发布到博客** `publish-blog` | `blog` | 返回「博客模块未启用，无法发布或同步博客」 |
| 精读详情：**同步博客** `sync-blog` | `blog` | 同上 |
| 学习页：**同步博客草稿** | `blog` | 返回「博客模块未启用，无法同步博客草稿」 |
| Integration 连通性测试：`tts` / 向量库 / `astrbot` 项 | `tts` / `knowledge` / `chat` | 返回「已跳过 / 未启用」，非失败 |

示例：

```vue
<el-button v-if="caps.enabled('blog')" @click="publishBlog">发布到博客</el-button>
```

---

## 5. 全局 404 兜底

关闭模块后其 Controller 不注册，相关 API 直接返回 **404**。前端应在请求拦截器统一处理，给「该功能未启用」的友好提示，避免白屏或全局错误弹窗：

```ts
http.interceptors.response.use(undefined, (err) => {
  if (err.response?.status === 404) {
    // 可选：提示「该功能未启用或不存在」
  }
  return Promise.reject(err)
})
```

> 正常情况下按第 3、4 步隐藏入口后，用户不应触达已关模块的 API；404 兜底是防御性措施（例如旧书签、深链接）。

---

## 6. 设置中心 Tab

设置页的模块 Tab 已由后端 `SettingModuleRegistry` 过滤——关闭模块的 `SettingModule` Bean 不存在，设置模块列表接口自然少掉该 Tab。

- 若前端**按接口返回**动态渲染 Tab：无需改动。
- 若前端**硬编码**了 Tab 列表：需改为按后端返回的设置模块列表渲染。

---

## 7. 精读异步任务（配套，若尚未接）

`batch-url` 批量入库现在默认**异步**：提交后返回任务对象（`KnowledgeReadingJobVO`），前端需轮询任务进度接口展示状态。完整轮询契约（字段、状态机、错误处理、同步调试开关）见 [../ai-reading-workbench/06-v2-frontend-sync.md](../ai-reading-workbench/06-v2-frontend-sync.md)。

---

## 8. 联调自测清单

| 场景 | 启动参数 | 前端预期 |
| --- | --- | --- |
| 默认全开 | 无 | 所有菜单/按钮正常 |
| 关博客 | `--app.modules.blog=false` | 博客菜单消失；精读「发布/同步博客」、学习「同步博客草稿」按钮隐藏 |
| 关学习 | `--app.modules.study=false` | 学习菜单消失，`/study/**` 路由被守卫拦截 |
| 关 TTS | `--app.modules.tts=false` | 语音合成菜单消失；Integration 页 TTS 项显示「已跳过」 |
| 关运维 | `--app.modules.ops=false` | 运维看板菜单消失（业务操作不受影响，审计静默） |

> 本地起后端：`mvn -DskipTests spring-boot:run -Dspring-boot.run.arguments="--app.modules.blog=false"`
