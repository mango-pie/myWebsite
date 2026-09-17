# GPT-SoVITS 前端 TTS API 调用文档

本文档说明如何在前端（浏览器 / Web 应用）中调用 GPT-SoVITS API，**传入文本，返回并播放音频**。

---

## 1. 前置准备

### 1.1 启动 API 服务

在项目根目录双击 **`api模式.bat`**，或执行：

```bat
runtime\python.exe -I api_v2.py
```

默认监听：

| 项目 | 值 |
|------|-----|
| 地址 | `http://127.0.0.1:9880` |
| 推理接口 | `POST /tts` |
| 配置文件 | `GPT_SoVITS/configs/tts_infer.yaml` |

自定义端口示例：

```bat
runtime\python.exe -I api_v2.py -a 127.0.0.1 -p 9880
```

### 1.2 准备参考音频

API **不会**通过 POST 上传参考音频文件，需要传入 **服务端本机路径**：

- 格式：wav / mp3 等常见格式
- 时长：**3～10 秒**（超过 10 秒可能报错）
- 示例路径：`E:/Quark/gpt-vot/GPT-SoVITS-v2pro-20250604/ref.wav`

达妮娅模型权重已在 `tts_infer.yaml` 的 `custom` 段配置，启动后自动加载，一般无需额外切换模型。

### 1.3 跨域（CORS）说明

浏览器从网页调用 API 时，若页面域名/端口与 `9880` 不同，会触发 **CORS 跨域限制**。

可选方案（任选其一）：

**方案 A：开发环境反向代理（推荐）**

在 Vite / Webpack 中把 `/tts` 代理到 `http://127.0.0.1:9880`，前端请求同源地址即可。

**方案 B：为 API 开启 CORS**

在 `api_v2.py` 中加入（需自行修改服务端）：

```python
from fastapi.middleware.cors import CORSMiddleware

APP.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],          # 生产环境请改为具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
```

**方案 C：使用项目自带的演示页**

直接打开 `docs/tts-demo.html`（需先按方案 B 开启 CORS，或通过本地静态服务器 + 代理访问）。

---

## 2. 接口说明

### 2.1 文本转语音

```
POST http://127.0.0.1:9880/tts
Content-Type: application/json
```

#### 请求体

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `text` | string | ✅ | 要合成的文本 |
| `text_lang` | string | ✅ | 合成文本语种，中文用 `zh` |
| `ref_audio_path` | string | ✅ | 参考音频在**服务器上**的路径 |
| `prompt_lang` | string | ✅ | 参考文本语种，中文用 `zh` |
| `prompt_text` | string | | 参考音频对应文本，建议填写 |
| `speed_factor` | number | | 语速，默认 `1.0` |
| `media_type` | string | | 输出格式：`wav`（默认）/ `ogg` / `aac` / `raw` |
| `streaming_mode` | bool \| int | | 流式返回，默认 `false` |
| `top_k` | int | | 采样参数，默认 `15` |
| `top_p` | float | | 默认 `1` |
| `temperature` | float | | 默认 `1` |
| `repetition_penalty` | float | | 重复惩罚，默认 `1.35` |
| `text_split_method` | string | | 切句方式，默认 `cut5` |
| `parallel_infer` | bool | | 并行推理，默认 `true` |

#### 支持的语种（v2ProPlus）

`zh`、`en`、`ja`、`yue`、`ko`、`auto`、`all_zh`、`all_ja`、`all_yue`、`all_ko` 等。

#### 响应

| 状态 | 内容 |
|------|------|
| **200** | 音频二进制流，`Content-Type: audio/wav`（或对应格式） |
| **400** | JSON 错误信息，如 `{"message": "text is required"}` |

#### 最小请求示例

```json
{
  "text": "你好，我是达妮娅。",
  "text_lang": "zh",
  "ref_audio_path": "E:/Quark/gpt-vot/output.wav_0009342720.wav",
  "prompt_text": "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽",
  "prompt_lang": "zh"
}
```

### 2.2 其他辅助接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/set_gpt_weights?weights_path=...` | 切换 GPT 权重 |
| GET | `/set_sovits_weights?weights_path=...` | 切换 SoVITS 权重 |
| GET | `/set_refer_audio?refer_audio_path=...` | **预置参考音频（推荐启动后调用一次）** |
| GET/POST | `/control` | `{"command": "restart"}` 或 `"exit"` |

### 2.3 参考音频缓存（避免重复读取）

服务端对参考音频和参考文本做了**内存缓存**：路径或文本不变时，不会重复从磁盘读取 wav，也不会重复计算 Hubert / BERT 特征。

#### 推荐固定配置（达妮娅示例）

若长期使用同一段参考音色，请固定以下两项，**每次请求保持不变**：

| 配置项 | 推荐值 |
|--------|--------|
| 参考音频路径 | `E:/Quark/gpt-vot/output.wav_0009342720.wav` |
| 参考文本 | `怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽` |

> **路径格式必须始终一致。** 请统一使用正斜杠 `E:/Quark/...`，不要混用 `E:\Quark\...` 与 `E:/Quark/...`，否则会被视为不同路径而触发重新读取。

#### 缓存规则

| 字段 | 何时重新处理 |
|------|--------------|
| `ref_audio_path` | 路径字符串变化，或服务重启后首次请求 |
| `prompt_text` | 文本内容变化 |

以下情况**仍会执行**（无法缓存）：

- 每次合成新 `text` 时的目标文本预处理（BERT / 分词）
- GPT + SoVITS 推理本身

#### 推荐流程

```
1. 启动 api模式.bat（保持常驻，勿频繁重启）
       ↓
2. 调用一次 GET /set_refer_audio 预加载参考音频
       ↓
3. 后续 POST /tts 时：
   - ref_audio_path、prompt_text 始终传相同固定值
   - 仅修改 text（要合成的目标台词）
       ↓
4. 服务端命中缓存，跳过参考音频读盘
```

**预加载示例（浏览器或 curl 均可）：**

```http
GET http://127.0.0.1:9880/set_refer_audio?refer_audio_path=E:/Quark/gpt-vot/output.wav_0009342720.wav
```

返回 `{"message":"success"}` 表示参考音频已载入内存。

> 注意：`POST /tts` 目前仍**要求**传 `ref_audio_path`（API 校验），但传入与缓存相同的路径时**不会重复读文件**。

#### 固定参考下的 POST 示例

```json
{
  "text": "你好，今天我们去冒险吧。",
  "text_lang": "zh",
  "ref_audio_path": "E:/Quark/gpt-vot/output.wav_0009342720.wav",
  "prompt_text": "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽",
  "prompt_lang": "zh",
  "parallel_infer": true,
  "text_split_method": "cut0"
}
```

短句合成建议 `text_split_method: "cut0"`（不切句，略快）。

### 2.4 性能优化建议

| 优化项 | 说明 |
|--------|------|
| 固定参考音频路径 | 见 §2.3，避免重复读盘 |
| 固定 `prompt_text` | 参考文本不变则跳过 BERT 重算 |
| 启动后预加载 | 调用 `/set_refer_audio` |
| API 常驻 | 重启会清空缓存，且首次请求有 CUDA 预热 |
| GPU + 半精度 | `tts_infer.yaml` 中 `device: cuda`、`is_half: true` |
| `parallel_infer: true` | 默认已开启，并行 GPT 推理 |
| 短句 + `cut0` | 单句 20～50 字，不切句 |
| `top_k: 12` | 略降采样开销，音质影响通常不大 |
| 长文 | `batch_size: 10~20`，`text_split_method: "cut5"` |
| 体感首包快 | `streaming_mode: 3`（总耗时可能略增） |
| 总耗时最短 | `streaming_mode: false` |

**预期耗时（参考）：** v2ProPlus 在 4060 Ti + GPU 下，短句通常 1～3 秒；若一句短话超过 10 秒，请检查是否实际跑在 CPU 上（控制台是否出现 `CUDA is not available`）。

---

## 3. 前端核心流程

```
页面 / 应用启动
    ↓
GET /set_refer_audio（一次性预加载固定参考音频）
    ↓
用户输入 text
    ↓
POST /tts（ref_audio_path、prompt_text 固定不变，只改 text）
    ↓
response.ok === true ?
    ├─ 是 → response.blob() → URL.createObjectURL → <audio> 播放 / 下载
    └─ 否 → response.json() → 展示错误信息
```

---

## 4. 代码示例

### 4.1 原生 JavaScript（fetch + 参考音频预加载）

```javascript
const API_BASE = "http://127.0.0.1:9880";

/** 固定参考配置：路径与文本请勿随意改动，以保证服务端缓存命中 */
const TTS_REF = {
  refAudioPath: "E:/Quark/gpt-vot/output.wav_0009342720.wav",
  promptText: "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽",
  promptLang: "zh",
  textLang: "zh",
};

/** 启动后调用一次，预加载参考音频到内存 */
async function initRefAudio() {
  const path = encodeURIComponent(TTS_REF.refAudioPath);
  const res = await fetch(`${API_BASE}/set_refer_audio?refer_audio_path=${path}`);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || "预加载参考音频失败");
  }
}

/**
 * 文本转语音，返回可播放的 Blob URL
 * @param {string} text - 要合成的文本（唯一每次变化的字段）
 */
async function textToSpeech(text) {
  const response = await fetch(`${API_BASE}/tts`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      text,
      text_lang: TTS_REF.textLang,
      ref_audio_path: TTS_REF.refAudioPath,
      prompt_text: TTS_REF.promptText,
      prompt_lang: TTS_REF.promptLang,
      media_type: "wav",
      parallel_infer: true,
      text_split_method: "cut0",
    }),
  });

  if (!response.ok) {
    const err = await response.json().catch(() => ({}));
    throw new Error(err.message || `HTTP ${response.status}`);
  }

  const blob = await response.blob();
  return URL.createObjectURL(blob);
}

// 使用示例
async function main() {
  await initRefAudio(); // 页面加载时执行一次
  const audioUrl = await textToSpeech("你好，我是达妮娅。");
  const audio = new Audio(audioUrl);
  audio.onended = () => URL.revokeObjectURL(audioUrl);
  await audio.play();
}

main();
```

### 4.2 封装为可复用类

```javascript
class GPTSoVITSTTS {
  constructor(options = {}) {
    this.baseUrl = options.baseUrl ?? "http://127.0.0.1:9880";
    this.refAudioPath =
      options.refAudioPath ?? "E:/Quark/gpt-vot/output.wav_0009342720.wav";
    this.promptText =
      options.promptText ??
      "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽";
    this.promptLang = options.promptLang ?? "zh";
    this.textLang = options.textLang ?? "zh";
    this.refLoaded = false;
  }

  /** 预加载参考音频，建议在应用 init 时调用一次 */
  async initRefAudio() {
    const path = encodeURIComponent(this.refAudioPath);
    const res = await fetch(`${this.baseUrl}/set_refer_audio?refer_audio_path=${path}`);
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || "预加载参考音频失败");
    }
    this.refLoaded = true;
  }

  async synthesize(text, extra = {}) {
    const res = await fetch(`${this.baseUrl}/tts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text,
        text_lang: this.textLang,
        ref_audio_path: this.refAudioPath,
        prompt_text: this.promptText,
        prompt_lang: this.promptLang,
        media_type: "wav",
        parallel_infer: true,
        text_split_method: "cut0",
        ...extra,
      }),
    });

    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.message || `合成失败: ${res.status}`);
    }

    return res.blob();
  }

  async play(text) {
    const blob = await this.synthesize(text);
    const url = URL.createObjectURL(blob);
    const audio = new Audio(url);
    audio.onended = () => URL.revokeObjectURL(url);
    await audio.play();
    return url;
  }

  async download(text, filename = "output.wav") {
    const blob = await this.synthesize(text);
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    URL.revokeObjectURL(url);
  }
}

// 使用
const tts = new GPTSoVITSTTS();
await tts.initRefAudio(); // 启动时一次
await tts.play("今天天气不错。"); // 之后只传不同 text
```

### 4.3 Axios

```javascript
import axios from "axios";

async function synthesize(text) {
  const { data } = await axios.post(
    "http://127.0.0.1:9880/tts",
    {
      text,
      text_lang: "zh",
      ref_audio_path: "E:/Quark/gpt-vot/output.wav_0009342720.wav",
      prompt_text: "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽",
      prompt_lang: "zh",
      parallel_infer: true,
      text_split_method: "cut0",
    },
    { responseType: "blob" }
  );
  return URL.createObjectURL(data);
}
```

### 4.4 React 示例

```jsx
import { useState, useRef, useEffect } from "react";

const API = "http://127.0.0.1:9880";
const REF_AUDIO = "E:/Quark/gpt-vot/output.wav_0009342720.wav";
const PROMPT_TEXT =
  "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽";

export default function TTSPlayer() {
  const [text, setText] = useState("你好，我是达妮娅。");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [ready, setReady] = useState(false);
  const audioRef = useRef(null);

  useEffect(() => {
    fetch(`${API}/set_refer_audio?refer_audio_path=${encodeURIComponent(REF_AUDIO)}`)
      .then((r) => (r.ok ? setReady(true) : Promise.reject()))
      .catch(() => setError("参考音频预加载失败"));
  }, []);

  const handleSynthesize = async () => {
    setLoading(true);
    setError("");
    try {
      const res = await fetch(`${API}/tts`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          text,
          text_lang: "zh",
          ref_audio_path: REF_AUDIO,
          prompt_text: PROMPT_TEXT,
          prompt_lang: "zh",
          parallel_infer: true,
          text_split_method: "cut0",
        }),
      });

      if (!res.ok) {
        const err = await res.json();
        throw new Error(err.message);
      }

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      if (audioRef.current) {
        audioRef.current.src = url;
        audioRef.current.onloadeddata = () => audioRef.current.play();
      }
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <textarea value={text} onChange={(e) => setText(e.target.value)} rows={4} />
      <button onClick={handleSynthesize} disabled={loading || !ready}>
        {loading ? "合成中…" : ready ? "合成并播放" : "参考音频加载中…"}
      </button>
      {error && <p style={{ color: "red" }}>{error}</p>}
      <audio ref={audioRef} controls />
    </div>
  );
}
```

### 4.5 Vue 3 示例

```vue
<script setup>
import { ref, onMounted } from "vue";

const text = ref("你好，我是达妮娅。");
const loading = ref(false);
const audioUrl = ref("");
const error = ref("");
const ready = ref(false);

const API = "http://127.0.0.1:9880";
const REF_AUDIO = "E:/Quark/gpt-vot/output.wav_0009342720.wav";
const PROMPT_TEXT =
  "怎么啊？如果有你在也不放心，那就干脆给我也装个限制器或者炸弹喽";

onMounted(async () => {
  const r = await fetch(
    `${API}/set_refer_audio?refer_audio_path=${encodeURIComponent(REF_AUDIO)}`
  );
  ready.value = r.ok;
});

async function synthesize() {
  loading.value = true;
  error.value = "";
  try {
    const res = await fetch(`${API}/tts`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text: text.value,
        text_lang: "zh",
        ref_audio_path: REF_AUDIO,
        prompt_text: PROMPT_TEXT,
        prompt_lang: "zh",
        parallel_infer: true,
        text_split_method: "cut0",
      }),
    });
    if (!res.ok) {
      const err = await res.json();
      throw new Error(err.message);
    }
    const blob = await res.blob();
    if (audioUrl.value) URL.revokeObjectURL(audioUrl.value);
    audioUrl.value = URL.createObjectURL(blob);
  } catch (e) {
    error.value = e.message;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <textarea v-model="text" rows="4" />
  <button @click="synthesize" :disabled="loading || !ready">
    {{ loading ? "合成中…" : ready ? "合成语音" : "参考音频加载中…" }}
  </button>
  <p v-if="error" style="color: red">{{ error }}</p>
  <audio v-if="audioUrl" :src="audioUrl" controls autoplay />
</template>
```

---

## 5. Vite 开发代理配置

若前端跑在 `http://localhost:5173`，在 `vite.config.js` 中添加：

```javascript
export default {
  server: {
    proxy: {
      "/api/tts": {
        target: "http://127.0.0.1:9880",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ""),
      },
    },
  },
};
```

前端请求改为：

```javascript
fetch("/api/tts", { method: "POST", ... })
```

---

## 6. 流式播放（可选）

设置 `streaming_mode: 1` 可分段返回 wav 流，适合长文本：

```javascript
const response = await fetch(`${API_BASE}/tts`, {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    text: "很长的文本……",
    text_lang: "zh",
    ref_audio_path: REF_AUDIO,
    prompt_text: PROMPT_TEXT,
    prompt_lang: "zh",
    streaming_mode: 1,
    media_type: "wav",
  }),
});

const blob = await response.blob();
const url = URL.createObjectURL(blob);
new Audio(url).play();
```

`streaming_mode` 取值：

| 值 | 说明 |
|----|------|
| `0` / `false` | 非流式，一次性返回 |
| `1` / `true` | 流式，质量最高 |
| `2` | 流式，中等质量 |
| `3` | 流式，速度更快 |

---

## 7. 常见错误

| 错误信息 | 原因 | 处理 |
|----------|------|------|
| CORS blocked | 浏览器跨域 | 见 §1.3 开启 CORS 或配置代理 |
| `ref_audio_path is required` | 未传参考音频路径 | 检查 JSON 字段名与路径 |
| `text is required` | 文本为空 | 确保 `text` 非空 |
| `text_lang is not supported` | 语种不支持 | 使用 `zh` / `en` 等合法值 |
| `tts failed` | 推理异常 | 检查参考音频时长、路径是否存在 |
| Failed to fetch | API 未启动 | 先运行 `api模式.bat` |
| 参考音频在 3~10 秒范围外 | 音频过短或过长 | 换一段 3～10 秒的 wav |
| 感觉每次都很慢 | 路径写法不一致导致重复读盘 | 统一用 `E:/Quark/...`，见 §2.3 |
| 重启后首条请求慢 | 缓存清空 + CUDA 预热 | 启动后先调 `/set_refer_audio` 并发一条短文本预热 |
| 控制台打印参考文本但很快 | 正常日志，不代表重复读文件 | 只有路径变化才会 `set_ref_audio` |

---

## 8. 演示页面

项目提供了可直接使用的演示页：

```
docs/tts-demo.html
```

用法：

1. 启动 `api模式.bat`
2. 按 §1.3 处理跨域（推荐在 `api_v2.py` 开启 CORS）
3. 用浏览器打开 `docs/tts-demo.html`
4. 默认已预填推荐参考路径与文本（见 §2.3），点击「合成并播放」

---

## 9. 安全提示

- API 默认只监听 `127.0.0.1`，仅本机可访问，适合本地开发。
- 若对外网开放（`-a 0.0.0.0`），务必加鉴权与限流，勿暴露在未受信网络。
- `ref_audio_path` 为服务端路径，前端无法直接上传本地文件；如需上传能力，需自行实现文件上传接口或使用固定参考音频。

---

## 10. 经 Ai-Backend 代理调用（推荐）

管理端统一走 Spring Boot BFF，**无需 CORS**，需 **administrator** 登录（Session Cookie，`credentials: 'include'`）。

| 项目 | 值 |
|------|-----|
| Base | `http://localhost:8123/api/tts` |
| 鉴权 | `administrator` 角色 |

### 10.1 接口对照

| 原 GPT-SoVITS | Java BFF |
|---------------|-----------|
| `GET /set_refer_audio` | `POST /api/tts/ref/init?voiceId=` |
| `POST /tts` | `POST /api/tts/synthesize`（Body 仅传 `text`、`voiceId` 可选） |
| — | `GET /api/tts/health` |
| — | `GET /api/tts/config` |
| — | `GET /api/tts/voice/list` |
| — | `POST /api/tts/voice/select` `{ "id": 1 }` |
| — | `POST /api/tts/voice/add`（multipart 上传参考音） |

### 10.2 前端示例

```javascript
const API = "/api/tts"; // 与后端同源

async function initTts() {
  await fetch(`${API}/ref/init`, { method: "POST", credentials: "include" });
}

async function synthesize(text, voiceId) {
  const res = await fetch(`${API}/synthesize`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ text, voiceId }),
  });
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.message || "合成失败");
  }
  return URL.createObjectURL(await res.blob());
}

// 切换音色
async function selectVoice(id) {
  await fetch(`${API}/voice/select`, {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id }),
  });
}
```

### 10.3 部署说明

1. MySQL 执行 `src/main/resources/sql/tts_schema.sql`
2. 启动 GPT-SoVITS（`api模式.bat`，9880）
3. 启动 Ai-Backend；启动时会尝试预加载默认音色
4. `application.yml` 中 `gpt-sovits.*` 可按环境修改
