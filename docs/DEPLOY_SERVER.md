# 服务器完整部署手册

> **给 Claude Code / 自动化工具**：请按章节顺序执行，每步确认成功后再继续。  
> 环境：**Ubuntu 22.04+**、**2GB 内存**、**IP 访问**（暂不用域名/HTTPS）。  
> **本机关 Redis / 向量库 / 应用工坊** 的默认与 env 清单见 **[DEPLOY_LOWMEM.md](./DEPLOY_LOWMEM.md)**。

## 仓库与产物

| 项目 | GitHub | 说明 |
|------|--------|------|
| 前端 | https://github.com/mango-pie/MyWebsite-ui |
| 后端 | https://github.com/mango-pie/myWebsite | JAR：`target/AI-0.0.1-SNAPSHOT.jar` |

## 变量表（执行前替换）

```bash
export SERVER_IP="121.43.177.236"    # 你的公网 IP
export DEPLOY_USER="root"          # SSH 登录用户
export APP_ROOT="/opt/ai-backend"
export WEB_ROOT="/var/www/ai-frontend"
export DB_NAME="aiscene"
export DB_USER="aiscene"
export DB_PASSWORD="请填写强密码"
```

## 架构

```
浏览器 → Nginx:80
           ├─ /              → /var/www/ai-frontend (Vue dist)
           ├─ /api/          → 127.0.0.1:8123 (Spring Boot)
           └─ /{deployKey}/  → /opt/ai-backend/tmp/code_deploy/{key}/
Spring Boot → MySQL (aiscene)
            → Redis (可选，不可用时内存 Session)
            → 外部 API (DashScope / AstrBot)
```

---

## 第 1 步：SSH 登录与系统初始化

```bash
ssh ${DEPLOY_USER}@${SERVER_IP}

sudo apt update && sudo apt upgrade -y
sudo timedatectl set-timezone Asia/Shanghai
```

### 1.1 防火墙

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw --force enable
sudo ufw status
```

**不要**对公网开放 8123、3306、6379。

### 1.2 2GB Swap（强烈建议）

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 1.3 创建目录

```bash
sudo mkdir -p ${APP_ROOT}/tmp/code_output ${APP_ROOT}/tmp/code_deploy
sudo mkdir -p /app/uploads /app/tts-ref
sudo mkdir -p ${WEB_ROOT}
sudo chown -R www-data:www-data ${APP_ROOT} /app/uploads /app/tts-ref ${WEB_ROOT}
```

---

## 第 2 步：安装依赖

```bash
sudo apt install -y openjdk-17-jre-headless mysql-server nginx git curl
java -version   # 应显示 17.x
```

### 2.1 MySQL 配置（2GB 优化）

```bash
sudo tee /etc/mysql/mysql.conf.d/99-ai-lowmem.cnf <<'EOF'
[mysqld]
innodb_buffer_pool_size = 128M
max_connections = 50
performance_schema = OFF
EOF
sudo systemctl restart mysql
```

### 2.2 创建数据库与用户

```bash
sudo mysql <<EOF
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
EOF
```

### 2.3 Redis（可选，2GB 可跳过）

若安装：

```bash
sudo apt install -y redis-server
echo "maxmemory 64mb" | sudo tee -a /etc/redis/redis.conf
echo "maxmemory-policy allkeys-lru" | sudo tee -a /etc/redis/redis.conf
sudo systemctl restart redis-server
```

不安装时请在 `/etc/ai-backend.env` 写 `APP_REDIS_MODE=off`（`prod` 已默认 off）。不要依赖 `auto` 去猜。

---

## 第 3 步：导入数据库

在**服务器**上克隆后端仓库（或仅复制 SQL 文件）：

```bash
cd /tmp
git clone https://github.com/mango-pie/myWebsite.git
cd myWebsite/src/main/resources/sql
```

**按顺序**导入（全新库）：

```bash
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < schema_core.sql
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < chat_conversation_schema.sql
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < diary_schema.sql
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < study_schema.sql
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < tts_schema.sql
```

验证：

```bash
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} -e "SHOW TABLES;"
# 应包含 user, app, blog_post, chat_conversation, diary_entry, study_list 等
```

> **已有数据迁移**：从本地 Windows 导出完整库  
> `mysqldump -u root -p aiscene > aiscene_full.sql`，上传后 `mysql aiscene < aiscene_full.sql`

---

## 第 4 步：部署后端

### 4.1 获取 JAR

**方式 A — 服务器构建**（需安装 Maven 或仅上传 JAR）：

```bash
# 本地 Windows 构建后上传（推荐，省服务器内存）
scp target/AI-0.0.1-SNAPSHOT.jar ${DEPLOY_USER}@${SERVER_IP}:${APP_ROOT}/
```

**方式 B — GitHub Actions Artifact**：在 myWebsite 仓库 Actions 页下载 `ai-backend-jar`。

```bash
sudo cp AI-0.0.1-SNAPSHOT.jar ${APP_ROOT}/
sudo chown www-data:www-data ${APP_ROOT}/AI-0.0.1-SNAPSHOT.jar
```

### 4.2 环境变量

```bash
sudo cp /tmp/myWebsite/deploy/env/ai-backend.env.example /etc/ai-backend.env
sudo chmod 600 /etc/ai-backend.env
sudo nano /etc/ai-backend.env   # 填入 DB_PASSWORD、API Key 等
```

**环境变量对照表**（与 `application.yml` 一致）：

| 变量 | 必填 | 说明 |
|------|------|------|
| `DB_PASSWORD` | 是 | MySQL 密码 |
| `DASHSCOPE_API_KEY` | 是 | 聊天/Agent/分段 |
| `CHAT_IMAGE_CAPTION_API_KEY` | 是 | 图片理解（可与上相同） |
| `CODEGEN_API_KEY` | 是 | AI 实验室代码生成 |
| `ASTRBOT_BASE_URL` | 聊天需要 | 外部 AstrBot 地址 |
| `ASTRBOT_API_KEY` | 聊天需要 | AstrBot 密钥 |
| `APP_DEPLOY_HOST` | 是 | `http://SERVER_IP` |
| `UPLOAD_BASE_URL` | 是 | `http://SERVER_IP/api` |
| `REDIS_*` | 否 | 未装 Redis 可省略 |

IP 模式示例：

```bash
APP_DEPLOY_HOST=http://121.43.177.236
UPLOAD_BASE_URL=http://121.43.177.236/api
```

### 4.3 systemd 服务

```bash
sudo cp /tmp/myWebsite/deploy/systemd/ai-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable ai-backend
sudo systemctl start ai-backend
sudo systemctl status ai-backend
```

查看日志：

```bash
journalctl -u ai-backend -f --no-pager
```

验证 API（在服务器上）：

```bash
curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8123/api/v3/api-docs
# 期望 200
```

---

## 第 5 步：部署前端

### 方式 A — 使用 Git 中已构建的 dist（推荐）

```bash
cd /tmp
git clone https://github.com/mango-pie/MyWebsite-ui.git
sudo rsync -a --delete MyWebsite-ui/AI-frontend/dist/ ${WEB_ROOT}/
sudo chown -R www-data:www-data ${WEB_ROOT}
```

### 方式 B — 需修改 API 地址时重新构建

在**本地 Windows**：

```bash
cd AI-frontend
cp .env.production.example .env.production
```

编辑 `.env.production`（IP + 同域 Nginx 反代）：

```env
VITE_API_BASE_URL=/api
VITE_PREVIEW_BASE_URL=/api/static
VITE_DEPLOY_BASE_URL=http://121.43.177.236
```

```bash
npm ci
npm run build-only
scp -r dist/* ${DEPLOY_USER}@${SERVER_IP}:${WEB_ROOT}/
```

---

## 第 6 步：配置 Nginx

```bash
sudo cp /tmp/myWebsite/deploy/nginx/ai-site.conf /etc/nginx/sites-available/ai-site.conf
sudo sed -i "s/SERVER_IP/${SERVER_IP}/g" /etc/nginx/sites-available/ai-site.conf
sudo ln -sf /etc/nginx/sites-available/ai-site.conf /etc/nginx/sites-enabled/
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

浏览器访问：`http://SERVER_IP/`

---

## 第 7 步：验收清单

| # | 检查项 | 命令/操作 | 期望 |
|---|--------|-----------|------|
| 1 | 首页 | 浏览器打开 `http://SERVER_IP/` | 页面正常 |
| 2 | API | `curl http://SERVER_IP/api/v3/api-docs` | HTTP 200 |
| 3 | 注册 | 前端注册新用户 | 成功 |
| 4 | 登录 | 登录后刷新仍保持 | Session 有效 |
| 5 | 博客 | 打开 `/blog` | 列表可加载 |
| 6 | 聊天 SSE | 发送一条 AI 消息 | 流式输出 |
| 7 | 图片上传 | 博客/聊天上传图片 | 可访问 `/api/uploads/...` |
| 8 | AI 实验室 | 生成并部署小应用 | 返回 `http://SERVER_IP/{key}/` 可打开 |
| 9 | 音乐播放器 | 搜索并播放（需附录 D） | 有结果、可播放 |

---

## 第 8 步：日常更新

### 更新前端

```bash
cd /tmp && rm -rf MyWebsite-ui && git clone https://github.com/mango-pie/MyWebsite-ui.git
sudo rsync -a --delete MyWebsite-ui/AI-frontend/dist/ ${WEB_ROOT}/
```

### 更新后端

```bash
# 上传新 JAR 后
sudo systemctl stop ai-backend
sudo cp AI-0.0.1-SNAPSHOT.jar ${APP_ROOT}/
sudo systemctl start ai-backend
```

### 数据库增量

```bash
mysql -u ${DB_USER} -p${DB_PASSWORD} ${DB_NAME} < 新脚本.sql
```

---

## 第 9 步：故障排查

| 现象 | 排查 |
|------|------|
| `502 Bad Gateway` | `journalctl -u ai-backend -n 50`；确认 8123 监听：`ss -tlnp \| grep 8123` |
| 登录后立即退出 | 确认前端 `VITE_API_BASE_URL=/api` 与页面同 IP；检查浏览器 Cookie |
| AI 聊天无响应 | 检查 `/etc/ai-backend.env` 中 `ASTRBOT_*`；服务器能否访问 AstrBot 地址 |
| 代码生成失败 | 检查 `CODEGEN_API_KEY`、DashScope 余额；`journalctl -u ai-backend` |
| 进程被 Kill | 内存不足：`free -h`；确认 Swap 已启用；降低 JVM `-Xmx` |
| 上传 404 | `ls -la /app/uploads`；权限应为 `www-data` |
| 部署链接 404 | `ls ${APP_ROOT}/tmp/code_deploy/`；Nginx deployKey location 是否生效 |
| MySQL 连接失败 | `mysql -u aiscene -p`；检查 `DB_PASSWORD` 与 systemd EnvironmentFile |
| 音乐播放器不可用 | 见附录 D.7；`systemctl status netease-api` |

---

## 第 10 步：给 Claude Code 的执行 Prompt

```
请阅读 https://github.com/mango-pie/myWebsite/blob/main/docs/DEPLOY_SERVER.md，
在 Ubuntu 服务器上完成从零部署。

已知信息：
- SERVER_IP=121.43.177.236
- SSH 用户 ubuntu，已可 sudo
- 使用 IP 访问，暂不需要 HTTPS
- 2GB 内存，请按文档配置 Swap 和 MySQL 低内存参数
- Redis 可选，暂不安装

请逐步执行第 1-7 步，每步汇报结果；失败时参考第 9 步排查。
API Key 和数据库密码我会放在 /etc/ai-backend.env，请你生成模板后等我填入。
```

---

## 附录 A：后续上域名 + HTTPS

```bash
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.com
```

然后修改：

- `/etc/ai-backend.env`：`APP_DEPLOY_HOST=https://your-domain.com`
- 前端 `.env.production` 重新 build 并上传
- `systemctl restart ai-backend`

## 附录 B：不部署 / 可选的功能

| 功能 | 说明 |
|------|------|
| GPT-SoVITS TTS | 2GB 不建议，`GPT_SOVITS_SEED_ENABLED=false` |
| 网易云 api-enhanced | 可选，见附录 D；约 100～200MB 内存 |

## 附录 C：配置文件路径速查

| 文件 | 仓库路径 |
|------|----------|
| Nginx 模板 | `deploy/nginx/ai-site.conf` |
| systemd 后端 | `deploy/systemd/ai-backend.service` |
| systemd 网易云 | `deploy/systemd/netease-api.service` |
| 环境变量模板 | `deploy/env/ai-backend.env.example` |
| 生产 Spring 配置 | `src/main/resources/application-prod.yml` |
| SQL 脚本 | `src/main/resources/sql/` |

## 附录 D：网易云 api-enhanced（可选，2GB 谨慎启用）

> **内存**：Node 进程约 **100～200MB**。2GB 全栈已跑 MySQL + Spring Boot 时偏紧，务必已配置 **2GB Swap**（第 1 步）。  
> **无需 API Key**：该服务是网易云接口代理。  
> **前端依赖**：悬浮播放器 `FloatingPlayer` 请求 `/netease-api`，封面走 `/netease-img`。

### D.1 是否值得部署

| 情况 | 建议 |
|------|------|
| 仅个人听歌、服务器已开 Swap | 可同机部署 |
| AI 聊天 + 代码生成经常同时用 | 暂缓，或外置到别的机器 |
| 不需要底部音乐播放器 | 跳过本节 |

### D.2 安装 Node.js（Ubuntu）

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
node -v   # v20.x
npm -v
```

### D.3 部署 api-enhanced

源码在前端仓库 `AI-frontend/api-enhanced/`（本仓库 `.gitignore` 未包含时，从本机 scp 或单独拷贝）。

```bash
sudo mkdir -p /opt/netease-api
sudo chown -R $USER:$USER /opt/netease-api

# 方式 A：从本机 scp
# scp -r AI-frontend/api-enhanced/* ${DEPLOY_USER}@${SERVER_IP}:/opt/netease-api/

# 方式 B：服务器上若 MyWebsite-ui 仓库含该目录
cd /tmp && git clone https://github.com/mango-pie/MyWebsite-ui.git
sudo rsync -a MyWebsite-ui/AI-frontend/api-enhanced/ /opt/netease-api/
cd /opt/netease-api
npm ci --omit=dev
```

验证启动：

```bash
cd /opt/netease-api
PORT=3000 node app.js
# 另开终端：curl -s "http://127.0.0.1:3000/search?keywords=test" | head -c 200
# Ctrl+C 后改由 systemd 托管
```

### D.4 systemd 服务

```bash
sudo cp /tmp/myWebsite/deploy/systemd/netease-api.service /etc/systemd/system/
sudo chown -R www-data:www-data /opt/netease-api
sudo systemctl daemon-reload
sudo systemctl enable netease-api
sudo systemctl start netease-api
sudo systemctl status netease-api
journalctl -u netease-api -f --no-pager
```

服务模板见 [`deploy/systemd/netease-api.service`](../deploy/systemd/netease-api.service)（含 `NODE_OPTIONS=--max-old-space-size=128`）。

### D.5 Nginx 反代

[`deploy/nginx/ai-site.conf`](../deploy/nginx/ai-site.conf) 已包含 `/netease-api/` 与 `/netease-img/`。若已部署过旧版 Nginx 配置，请合并后：

```bash
sudo cp /tmp/myWebsite/deploy/nginx/ai-site.conf /etc/nginx/sites-available/ai-site.conf
sudo sed -i "s/SERVER_IP/${SERVER_IP}/g" /etc/nginx/sites-available/ai-site.conf
sudo nginx -t && sudo systemctl reload nginx
```

前端 `neteaseMusic.ts` 使用相对路径 `/netease-api`，**无需**修改 `VITE_*` 环境变量。

### D.6 验收

| # | 操作 | 期望 |
|---|------|------|
| 1 | `curl -s "http://SERVER_IP/netease-api/search?keywords=晴天" \| head -c 300` | 返回 JSON |
| 2 | 浏览器打开站点，底部播放器搜索 | 有结果、可播放 |
| 3 | `free -h` | 可用内存仍 > 200MB 或 Swap 未打满 |

### D.7 故障排查

| 现象 | 处理 |
|------|------|
| 播放器提示「请确认 netease-api 已启动」 | `systemctl status netease-api`；`curl http://127.0.0.1:3000/search?keywords=a` |
| 502 on `/netease-api` | Nginx 是否 reload；`ss -tlnp \| grep 3000` |
| 封面不显示 | 检查 `/netease-img/` 反代 |
| 进程被 Kill | 确认 Swap；或调低 `NODE_OPTIONS=--max-old-space-size=96` |
| 灰色歌曲无法播放 | 网易策略限制，属 api-enhanced 能力边界 |

### D.8 外置部署（更省 2GB 内存）

将 api-enhanced 跑在另一台机器时，Nginx 中改为：

```nginx
location /netease-api/ {
    proxy_pass http://OTHER_HOST:3000/;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_read_timeout 60s;
}
```
