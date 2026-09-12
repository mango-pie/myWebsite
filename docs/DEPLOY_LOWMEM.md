# 2GB 小机器生产配置

> 适用：本仓库后端部署在 **约 2GB 内存** 的 VPS；**知识库（pgvector / MinIO）在另一台机器**；本机 **不装 Redis**；**不跑应用工坊（生成页面）**。  
> 逐步装机、Nginx、systemd 仍看 [DEPLOY_SERVER.md](./DEPLOY_SERVER.md)。增量升级看 [UPGRADE.md](./UPGRADE.md)。  
> 配置真相：[`application-prod.yml`](../src/main/resources/application-prod.yml) + [`deploy/env/ai-backend.env.example`](../deploy/env/ai-backend.env.example)。

## 1. 这台机器跑什么

| 要 | 不要 |
| --- | --- |
| MySQL + Spring Boot（`-Xms256m -Xmx512m`） | 本机 Redis |
| 博客、聊天、精读工作台、领域树 | 本机 PostgreSQL / pgvector |
| Nginx 反代 `/api` | 本机 MinIO |
| | TTS / GPT-SoVITS |
| | 应用工坊（生成页面、`app-lab`） |
| | 在这台机 `mvn package` |

精读笔记（`knowledge_note`）仍写 **本机 MySQL**。向量检索、对象存储走 **另一台知识库服务器**。因此：

- **不要**设 `APP_MODULES_KNOWLEDGE=false`（精读 / 领域树接口都挂在 knowledge 模块上，关掉会 404）。
- **要**设 `KNOWLEDGE_VECTOR_ENABLED=false`、`KNOWLEDGE_MINIO_ENABLED=false`（本机不建 PG 连接池、不连 MinIO）。

## 2. 内存怎么分

常驻大约：JVM 700～900MB + MySQL 250～400MB + 系统/Nginx。贴着 2GB，必须有 **2G Swap**（见 [DEPLOY_SERVER.md](./DEPLOY_SERVER.md) §1.2）。

**不要把 `-Xmx` 提到 1G**，MySQL 容易被 OOM Killer 干掉。

精读合蒸会短暂堆大字符串。prod 把材料上限收到 24 万字符；一次勾选 **1～3 个 URL**，不要拉满 5 篇长文。

## 3. prod 已写死的默认

`--spring.profiles.active=prod` 时：

| 项 | 值 | 作用 |
| --- | --- | --- |
| `app.redis.mode` | `off` | 不探测 Redis；Session / 门闩 / 读页缓存走内存（有条数上限） |
| `app.modules.tts` | `false` | 不加载 TTS、不做启动预热 |
| `app.modules.app-lab` | `false` | 不加载生成页面 |
| `knowledge.vector.enabled` | `false` | 不创建 pgvector 连接池 |
| `knowledge.minio.enabled` | `false` | 本机不传 MinIO |
| `knowledge.reading.max-merge-raw-chars` | `240000` | 降低合蒸峰值内存 |
| Hikari `maximum-pool-size` | `5` | 配合 MySQL `max_connections=50` |
| Tomcat `threads.max` | `30` | 个人站足够 |
| langchain4j `log-requests` | `false` | 避免精读材料打爆日志 |

本地开发仍用默认 profile：Redis `auto`、模块全开、向量库可连。

## 4. `/etc/ai-backend.env` 必填项

从仓库复制 [`deploy/env/ai-backend.env.example`](../deploy/env/ai-backend.env.example)，至少保证：

```bash
APP_REDIS_MODE=off
APP_MODULES_TTS=false
APP_MODULES_APP_LAB=false
APP_MODULES_KNOWLEDGE=true
APP_MODULES_READING=true
KNOWLEDGE_VECTOR_ENABLED=false
KNOWLEDGE_MINIO_ENABLED=false
KNOWLEDGE_READING_MAX_MERGE_RAW_CHARS=240000
```

再填 MySQL 密码、各 AI Key、`APP_DEPLOY_HOST`。然后：

```bash
sudo chmod 600 /etc/ai-backend.env
sudo systemctl restart ai-backend
journalctl -u ai-backend -n 80 --no-pager
```

启动日志里应看到 Redis `mode=off`；模块摘要里 `tts`、`app-lab` 为 off。

## 5. 升级已有服务器

1. 本机 Windows 构建 JAR，**不要在 2GB 上编译**。  
2. 备份库和旧 JAR（[UPGRADE.md](./UPGRADE.md)）。  
3. 把上一节变量 **合并进现有** `/etc/ai-backend.env`（不要整文件覆盖把 Key 冲掉）。  
4. 停服务 → 换 JAR → 启动。  
5. 若后台「集成设置」里 MinIO 仍为开：关掉。站点设置会覆盖 YAML。

## 6. 以后如果要改

| 需求 | 做法 |
| --- | --- |
| 本机再用 Redis | `APP_REDIS_MODE=on`，装 Redis 并 `maxmemory 64mb` |
| 本机连向量库 | `KNOWLEDGE_VECTOR_ENABLED=true`，并配 `KNOWLEDGE_VECTOR_DB_*`（指向那台知识库机或本机 PG） |
| 打开应用工坊 | `APP_MODULES_APP_LAB=true`（2GB **不建议**） |

前端可通过 `GET /api/app/modules` 看到 TTS / 应用工坊为关，相关菜单应变灰或隐藏。

## 7. 排错

| 现象 | 先查 |
| --- | --- |
| 进程消失 | `dmesg \| grep -i kill`；`free -h`；Swap 是否启用 |
| 精读 / 领域树 404 | 是否误关了 `APP_MODULES_KNOWLEDGE` |
| 入库 / 向量报错 | 本机 `KNOWLEDGE_VECTOR_ENABLED=false` 是预期；去知识库那台机操作 |
| 登录卡住、线程耗尽 | 不要用 `APP_REDIS_MODE=auto` 去扫一个不是 Redis 的端口；prod 保持 `off` |
