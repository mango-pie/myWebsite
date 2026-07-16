# 全站设置中心 P1 — 前端对接说明（集成与密钥）

> 依赖 P0。后端已实现 `integration` 模块。  
> 产品说明见 [03-ai-integrations.md](./03-ai-integrations.md)。

## 1. 侧栏

在「上传」之后增加：**集成与密钥** → `/admin/settings/integration`

## 2. API（沿用 P0）

| 方法 | 路径 |
| --- | --- |
| GET | `/api/admin/site-settings/integration/schema` |
| GET | `/api/admin/site-settings/integration` |
| PUT | `/api/admin/site-settings/integration` |
| POST | `/api/admin/site-settings/integration/reset` |
| POST | `/api/admin/site-settings/integration/test` |

### 测试连接

```http
POST /api/admin/site-settings/integration/test
Content-Type: application/json

{ "target": "knowledge_ai" }
```

`target` 支持：`knowledge_ai` | `agent` | `codegen` | `jina` | `astrbot` | `tts` | `minio`

响应：

```json
{
  "code": 0,
  "data": {
    "target": "knowledge_ai",
    "ok": true,
    "latencyMs": 120,
    "message": "连通成功"
  }
}
```

不回显 API Key。

## 3. 敏感字段交互

- GET：敏感值形态为 `{ "configured": true, "hint": "****abcd" }`（后四位为明文密钥后四位）。
- PUT：密钥类字段**留空 = 不修改**；填写新值则覆盖并加密入库。
- Password 输入框 + 占位符「不修改请留空」。

## 4. 表单分组建议

1. 知识库 AI  
2. Jina  
3. 聊天 / Agent / 图片转述 / 分段  
4. 代码生成  
5. AstrBot  
6. GPT-SoVITS / TTS 路径  
7. 上传与部署路径  
8. MinIO  

`danger` 字段（路径类）保存前 Confirm。  
每组可放「测试连接」按钮（对应上表 target）。

## 5. 运维提示

- 加解密主密钥：`SITE_SETTING_CRYPTO_SECRET` 或 `site-setting.crypto-secret`（环境变量，不进设置页）。
- 变更 `upload.path` 后静态资源映射可能仍用启动时路径，需重启生效（文案可提示）。

## 6. Key 清单

完整契约见 [01-module-keys.md](./01-module-keys.md) 第 5 节 `integration`。
