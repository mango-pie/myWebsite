# 音乐播放器后端数据设计方案

## 一、前端播放器数据结构

```typescript
interface Song {
  id: string;           // 歌曲唯一标识
  title: string;        // 歌曲名称
  artist: string;       // 艺术家
  album: string;        // 专辑
  coverUrl: string;      // 封面图URL
  audioUrl: string;      // 音频文件URL
  duration: number;      // 时长（秒）
  lyrics?: Lyric[];      // 歌词
}

interface Lyric {
  time: number;          // 时间戳（秒）
  text: string;          // 歌词文本
}
```

## 二、后端 API 设计

### 1. 获取播放链接

**接口**: `GET /api/music/player`

**参数**:
- `mid` (string, required): 歌曲MID

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "song": {
      "id": "00477",
      "mid": "0039k58J2v6f46",
      "title": "晴天",
      "singer": "周杰伦",
      "album": "叶惠美",
      "duration": 267,
      "coverUrl": "https://y.gtimg.cn/music/photo_new/T002R300x300M00000477.jpg",
      "audioUrl": "https://stream.com/api/music/play/0039k58J2v6f46"
    }
  },
  "message": "ok"
}
```

### 2. 搜索歌曲

**接口**: `GET /api/music/search`

**参数**:
- `keyword` (string, required): 搜索关键词
- `pageNum` (int, optional, default: 1): 页码
- `pageSize` (int, optional, default: 20): 每页数量

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": "00477",
        "mid": "0039k58J2v6f46",
        "title": "晴天",
        "singer": "周杰伦",
        "album": "叶惠美",
        "duration": 267,
        "coverUrl": "https://y.gtimg.cn/music/photo_new/T002R300x300M00000477.jpg"
      }
    ],
    "total": 100,
    "pageNum": 1,
    "pageSize": 20
  }
}
```

### 3. 获取歌单

**接口**: `GET /api/music/playlist/:id`

**参数**:
- `id` (string, required): 歌单ID

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "id": "playlist_001",
    "name": "流行音乐",
    "coverUrl": "https://y.gtimg.cn/...",
    "description": "热门流行歌曲合集",
    "songs": [
      {
        "id": "00477",
        "mid": "0039k58J2v6f46",
        "title": "晴天",
        "singer": "周杰伦",
        "album": "叶惠美",
        "duration": 267,
        "coverUrl": "https://y.gtimg.cn/..."
      }
    ],
    "songCount": 50
  }
}
```

### 4. 获取歌词

**接口**: `GET /api/music/lyric/:mid`

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "mid": "0039k58J2v6f46",
    "lyrics": [
      { "time": 0, "text": "故事的小黄花" },
      { "time": 5.2, "text": "从出生那年就飘着" },
      { "time": 10.5, "text": "童年的荡秋千" }
    ]
  }
}
```

### 5. 获取推荐歌曲

**接口**: `GET /api/music/recommend`

**响应示例**:
```json
{
  "code": 0,
  "data": {
    "songs": [
      {
        "id": "00477",
        "mid": "0039k58J2v6f46",
        "title": "晴天",
        "singer": "周杰伦",
        "coverUrl": "https://y.gtimg.cn/...",
        "audioUrl": "https://stream.com/api/music/play/0039k58J2v6f46"
      }
    ]
  }
}
```

## 三、音乐数据来源方案

### 方案一：使用 QQ 音乐 API（推荐）

**优点**：
- 音乐资源丰富，质量高
- 有官方开放平台支持
- 不需要自己存储音频文件

**缺点**：
- 部分音乐有版权限制
- 需要商业授权（可能需要付费）
- API 调用有频率限制

**可行性**：
- QQ 音乐有官方开放平台（腾讯音乐开放平台）
- 提供搜索、播放链接、歌词等 API
- 需要申请成为开发者

**注意**：
- 直接使用 QQ 音乐源文件地址可能被识别为盗链
- 建议通过自己的服务器代理转发音频流

### 方案二：自建音乐库

**实现方式**：
1. 使用爬虫从公开音乐源获取音频（需版权授权）
2. 用户上传音乐文件
3. 对接网易云音乐、酷狗等开放平台

**优点**：
- 数据完全可控
- 无第三方依赖

**缺点**：
- 版权风险
- 存储成本
- 维护工作量大

### 方案三：使用免费音乐 API

**可选服务**：
- Spotify API（需要付费 Premium）
- Deezer API（部分免费）
- SoundCloud API
- 免费音乐库如 Free Music Archive

**优点**：
- 免费或低成本
- 快速上线

**缺点**：
- 音乐库规模有限
- 功能受限

## 四、推荐架构设计

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   前端播放器     │ ──▶ │   后端 API      │ ──▶ │   数据源        │
│                 │     │                 │     │                 │
│  - 播放控制     │     │  - 鉴权         │     │  - QQ音乐API   │
│  - 播放列表     │     │  - 数据聚合     │     │  - 自建音乐库   │
│  - 歌词同步     │     │  - 音频代理     │     │  - 第三方API   │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

### 核心功能模块

1. **搜索模块**：对接 QQ 音乐搜索 API
2. **播放模块**：通过代理转发音频流，避免跨域
3. **歌词模块**：获取并缓存歌词数据
4. **歌单模块**：管理用户收藏的歌单

## 五、快速实现建议

### 阶段一：基础播放（1-2天）
- 使用 QQ 音乐 API 获取播放链接
- 通过后端代理转发音频流
- 实现基础搜索和播放

### 阶段二：完善功能（2-3天）
- 添加歌词同步
- 实现歌单功能
- 添加播放记录

### 阶段三：优化体验（1-2天）
- 添加推荐算法
- 优化缓存策略
- 用户体验打磨

## 六、注意事项

1. **版权合规**：确保音乐使用有合法授权
2. **跨域问题**：音频流建议通过后端代理
3. **接口稳定**：做好接口容错和降级处理
4. **用户体验**：做好加载状态和错误提示
