# 升级手册（旧版 → 当前版）

> 适用场景：**服务器上已部署过一个较老版本**，现在要升级到包含「模块解耦 / 精读异步任务队列 / 站点设置 / 运维可观测」的当前版本。  
> **2GB + 知识库在其它机器**：先读 [DEPLOY_LOWMEM.md](./DEPLOY_LOWMEM.md)，再按本文备份与换 JAR。
> 全新部署请看 [DEPLOY_SERVER.md](DEPLOY_SERVER.md)，本文只讲**增量升级**。
>
> 仓库：后端 `https://github.com/mango-pie/myWebsite`，前端 `https://github.com/mango-pie/MyWebsite-ui`。
> 环境假设：Ubuntu + MySQL + systemd 托管（`ai-backend.service`），后端在 `127.0.0.1:8123`，Nginx 反代 `/api`。

---

## 0. 一句话风险提示

- **绝不要在生产库重跑 `sql/schema_core.sql`**：它含 `DROP TABLE IF EXISTS user / app / chat_history / blog_*`，会清空用户、博客、应用、聊天历史。升级只跑「增量脚本」。
- 升级前**先备份数据库和旧 JAR**。
- Redis 的健康探测只测 TCP 连通、不测真正 PING，配错端口会导致**每个请求卡死**，详见第 4 节。

---

## 1. 升级前备份（必做）

```bash
# 数据库
mysqldump -u aiscene -p aiscene > ~/aiscene_backup_$(date +%F_%H%M).sql

# 旧 JAR（回滚用）
sudo cp /opt/ai-backend/AI-0.0.1-SNAPSHOT.jar ~/AI-backup-$(date +%F_%H%M).jar
```

回滚方式：停服 → 覆盖回旧 JAR → 起服 →（如需要）`mysql aiscene < 备份.sql`。

---

## 2. 数据库增量迁移（升级核心）

### 2.1 先看现有表

```bash
mysql -u aiscene -p aiscene -e "SHOW TABLES;"
```

### 2.2 本版相比旧版新增/需要的表

| 脚本 | 涉及表 | 不建的后果 |
|------|--------|-----------|
| `site_setting_schema.sql` | `site_setting` | **每个请求都会报错**（`HttpAccessLogFilter` 每次读站点设置） |
| `site_setting_audit_schema.sql` | `site_setting_audit` | 设置变更审计不可用 |
| `knowledge_schema.sql` | `knowledge_base` / `knowledge_document` / `source_document` / `knowledge_note` / `knowledge_processing_task` / `knowledge_conversation` / `knowledge_message` / `knowledge_message_reference` | 知识库/精读整体不可用 |
| `knowledge_note_v1_alter.sql` | `knowledge_note`（ALTER） | 知识笔记 v1 字段缺失 |
| `knowledge_reading_job_schema.sql` | `knowledge_reading_job` | **本版新增**：精读合蒸异步任务队列不可用 |
| `ai_usage_log_schema.sql` | `ai_usage_log` | AI 用量统计不可用 |
| `ops_audit_log_schema.sql` | `ops_audit_log` | 运维操作审计不可用 |
| `biz_stat_daily_schema.sql` | `biz_stat_daily` | 每日业务统计不可用 |
| `http_access_log_schema.sql` | `http_access_log` | 访问日志不可用 |

> 若旧版更老，可能连 `chat_conversation` / `diary` / `study` / `tts` 也没有，一并补 `chat_conversation_schema.sql`、`diary_schema.sql`、`study_schema.sql`、`tts_schema.sql`。
> `knowledge_pgvector_schema.sql` 是 **PostgreSQL + pgvector** 专用，MySQL 生产**不要导**（RAG 向量检索为可选能力，需单独准备 pgvector）。

### 2.3 执行（除 `schema_core.sql` 外全部幂等，可安全重复）

```bash
cd /tmp && rm -rf myWebsite && git clone https://github.com/mango-pie/myWebsite.git
cd myWebsite/src/main/resources/sql

export DB_PASSWORD='你的数据库密码'

for f in \
  site_setting_schema.sql \
  site_setting_audit_schema.sql \
  knowledge_schema.sql \
  knowledge_reading_job_schema.sql \
  ai_usage_log_schema.sql \
  ops_audit_log_schema.sql \
  biz_stat_daily_schema.sql \
  http_access_log_schema.sql ; do
  echo ">>> $f"
  mysql -u aiscene -p"$DB_PASSWORD" aiscene < "$f"
done

# ALTER 脚本：列若已存在会报错，可忽略该错误
mysql -u aiscene -p"$DB_PASSWORD" aiscene < knowledge_note_v1_alter.sql
```

### 2.4 验证

```bash
mysql -u aiscene -p aiscene -e "SHOW TABLES LIKE 'site_setting';"
mysql -u aiscene -p aiscene -e "SHOW TABLES LIKE 'knowledge_reading_job';"
```

---

## 3. 环境变量差异

本版**基本不新增必填环境变量**，旧的 `/etc/ai-backend.env` 可沿用，仅需检查：

- AI 相关 Key 是否齐全：`DASHSCOPE_API_KEY`、`CHAT_IMAGE_CAPTION_API_KEY`、`CODEGEN_API_KEY`、`ASTRBOT_*`。
- `APP_DEPLOY_HOST` / `UPLOAD_BASE_URL` 与实际 IP/域名一致。
- Redis 相关见第 4 节。

### 模块开关（2GB / 知识库在其它机器）

`prod` 已默认：`APP_REDIS_MODE=off`、关 TTS 与应用工坊、本机不连 pgvector/MinIO。升级时把 `deploy/env/ai-backend.env.example` 里对应项合并进 `/etc/ai-backend.env`。

**不要**关 `APP_MODULES_KNOWLEDGE`：精读笔记仍在本库。向量库在另一台机时用：

```bash
APP_MODULES_TTS=false
APP_MODULES_APP_LAB=false
APP_REDIS_MODE=off
KNOWLEDGE_VECTOR_ENABLED=false
KNOWLEDGE_MINIO_ENABLED=false
```

> 命名遵循 Spring relaxed binding：`app.modules.tts` → `APP_MODULES_TTS`。关闭后对应接口返回 404，前端可通过 `GET /api/app/modules` 感知，详见 [module-decoupling/01-frontend-integration.md](module-decoupling/01-frontend-integration.md)。

---

## 4. Redis 注意事项（重要）

本版 **Session 与站点设置缓存都可走 Redis**。启动时按 `app.redis.mode` 决定是否启用。**`prod` 默认 `off`，不探测 Redis。**

若你在非 prod 使用 `auto`：探测会发 Redis `PING`。端口被其它服务占用仍可能导致异常，建议显式 `on` 或 `off`。

服务器上二选一：

- **装真 Redis**：`sudo apt install -y redis-server`，确认 `redis-cli ping` 返回 `PONG`，`/etc/ai-backend.env` 里 `REDIS_HOST=127.0.0.1`、`REDIS_PORT=6379` 指对；
- **不装 Redis**：显式设置 `APP_REDIS_MODE=off`（内存 Session），不要留 `auto` 去猜。

```bash
# /etc/ai-backend.env
# 不用 Redis（2GB / prod 推荐）：
APP_REDIS_MODE=off
# 要用 Redis 时再改为 on，并填 REDIS_HOST/PORT
```

> 提示：仓库 base `application.yml` 的 Redis 默认端口是本地开发值，**生产以 `application-prod.yml`（6379）+ 环境变量为准**，不受影响。

---

## 5. 获取并替换 JAR

### 5.1 构建产物

当前改动位于分支 `feature/module-decoupling`；GitHub Actions 仅在 **`main`** 推送时自动出包。两种方式：

- **本地构建上传（推荐）**
  ```bash
  ./mvnw clean package -DskipTests          # 产物 target/AI-0.0.1-SNAPSHOT.jar
  scp target/AI-0.0.1-SNAPSHOT.jar root@SERVER_IP:/opt/ai-backend/AI-0.0.1-SNAPSHOT.jar.new
  ```
- **或**先把分支合并/PR 到 `main`，在 Actions 下载 artifact `ai-backend-jar`。

### 5.2 替换并重启（systemd）

```bash
sudo systemctl stop ai-backend
sudo mv /opt/ai-backend/AI-0.0.1-SNAPSHOT.jar.new /opt/ai-backend/AI-0.0.1-SNAPSHOT.jar
sudo chown www-data:www-data /opt/ai-backend/AI-0.0.1-SNAPSHOT.jar
sudo systemctl start ai-backend

# 盯启动日志，重点看：建表报错、Redis 连接、Mapper 扫描
journalctl -u ai-backend -f --no-pager
```

---

## 6. 前端

- 纯后端升级：前端可不动。
- 若要让前端识别模块开关（隐藏被关闭模块的入口）：按 [module-decoupling/01-frontend-integration.md](module-decoupling/01-frontend-integration.md) 更新，然后
  ```bash
  cd /tmp && rm -rf MyWebsite-ui && git clone https://github.com/mango-pie/MyWebsite-ui.git
  sudo rsync -a --delete MyWebsite-ui/AI-frontend/dist/ /var/www/ai-frontend/
  sudo chown -R www-data:www-data /var/www/ai-frontend
  ```

---

## 7. 验收清单

| # | 检查 | 命令 / 操作 | 期望 |
|---|------|-------------|------|
| 1 | 服务存活 | `systemctl status ai-backend` | active (running) |
| 2 | 模块能力 | `curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8123/api/app/modules` | 200 |
| 3 | 登录接口不卡 | `curl -s -o /dev/null -w "%{http_code} %{time_total}\n" http://127.0.0.1:8123/api/user/get/login` | **秒回**（未登录也应快速返回，不能挂起） |
| 4 | 博客列表 | `curl -s "http://127.0.0.1:8123/api/blog/post/list/page/published?pageNum=1&pageSize=1"` | 返回 JSON |
| 5 | 登录保持 | 前端登录后刷新 | Session 有效 |
| 6 | 精读队列 | 触发一次精读合蒸 | 任务入队并可查询状态 |
| 7 | 内存 | `free -h` | 有余量 / Swap 未打满 |

> 第 3 项是本版的重点回归项：如果登录接口挂起，几乎都是 **Redis 配置问题**（回到第 4 节）。

---

## 8. 常见问题

| 现象 | 排查 |
|------|------|
| 每个请求卡住 / `ERR_CONNECTION_REFUSED` | Redis 探测误判：`redis-cli -h $REDIS_HOST -p $REDIS_PORT ping` 是否 `PONG`；不用 Redis 就设 `APP_REDIS_MODE=off`，重启（见第 4 节） |
| 启动即报 `Table 'aiscene.site_setting' doesn't exist` 之类 | 增量 SQL 未执行完整，补跑第 2 节脚本 |
| `502 Bad Gateway` | `journalctl -u ai-backend -n 80`；`ss -tlnp \| grep 8123` |
| 数据被清空 | 误跑了 `schema_core.sql`，用第 1 节备份恢复 |
| 进程被 OOM Kill | `free -h`；确认 Swap；调低 JVM `-Xmx` 或关闭部分模块（第 3 节） |

---

## 9. 升级步骤速查（TL;DR）

```text
1. mysqldump 备份 + 备份旧 JAR
2. 跑增量 SQL（不含 schema_core.sql）
3. 检查 /etc/ai-backend.env：Redis 指对 或 APP_REDIS_MODE=off
4. 上传新 JAR → systemctl stop → 替换 → start
5. journalctl 看日志 + 第 7 节验收（尤其登录接口不能卡）
```
