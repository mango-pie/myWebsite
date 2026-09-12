# 服务器部署实录 — 2026-06-20

> **给 Claude Code / 开发者**：按此文档可快速定位问题、更新项目、恢复服务。

---

## 1. 连接信息

| 项目 | 值 |
|------|-----|
| 公网 IP | `121.43.177.236` |
| SSH 密钥 | `E:\Quark\ai.pem` |
| SSH 用户 | `root` |
| 面板地址 | `https://121.43.177.236:8888/c114396b`（BT Panel） |

**SSH 连接命令**：
```bash
ssh -i "E:/Quark/ai.pem" -o StrictHostKeyChecking=no root@121.43.177.236
```

**SCP 上传文件**：
```bash
scp -i "E:/Quark/ai.pem" -o StrictHostKeyChecking=no 本地文件 root@121.43.177.236:/目标路径/
```

---

## 2. 服务器硬件 & 系统

| 项目 | 值 |
|------|-----|
| 系统 | Ubuntu 24.04.4 LTS |
| 内存 | 1.6 GB |
| Swap | 4 GB（`/www/swap`） |
| 磁盘 | 40 GB（已用 ~14 GB） |

---

## 3. 架构

```
浏览器 → http://121.43.177.236:80
           │
           ├─ Nginx :80
           │    ├─ /              → /var/www/ai-frontend (Vue SPA dist)
           │    ├─ /api/          → proxy → 127.0.0.1:8123/api/ (Spring Boot)
           │    └─ /{6位Key}/     → /opt/ai-backend/tmp/code_deploy/{Key}/
           │
           ├─ Spring Boot (:8123)
           │    ├─ MySQL (127.0.0.1:3306, aiscene)
           │    ├─ Redis (127.0.0.1:6379)
           │    └─ DashScope / AstrBot
           │
           └─ UFW：仅 22、80 对外
```

---

## 4. 已完成

- [x] Java 21 + Nginx 1.24 + MySQL 8.4 + Redis
- [x] 后端 JAR：`/opt/ai-backend/AI-0.0.1-SNAPSHOT.jar`（Spring Boot 3.5.11）
- [x] 前端 dist：`/var/www/ai-frontend/`
- [x] systemd 自启：`ai-backend.service`
- [x] 环境变量：`/etc/ai-backend.env`
- [x] Nginx 站点：`/etc/nginx/sites-available/ai-site.conf`
- [x] 数据库 `aiscene`：18 张表
- [x] DashScope + AstrBot API Key 已配置

---

## 5. 未做 / 不做的

| 项目 | 状态 | 说明 |
|------|------|------|
| HTTPS / 域名 | ❌ | 纯 IP 访问 |
| GPT-SoVITS TTS | ❌ 关闭 | 2GB 不建议 |
| Redis 密码 | ❌ | 仅 127.0.0.1 |
| 数据库定期备份 | ❌ | 建议加 cron |

---

## 6. 服务器关键路径

| 文件 | 路径 |
|------|------|
| 环境变量 | `/etc/ai-backend.env` |
| systemd | `/etc/systemd/system/ai-backend.service` |
| Nginx 站点 | `/etc/nginx/sites-available/ai-site.conf` |
| 后端 JAR | `/opt/ai-backend/AI-0.0.1-SNAPSHOT.jar` |
| 前端 | `/var/www/ai-frontend/` |
| 上传目录 | `/app/uploads/` |
| AI 实验室 | `/opt/ai-backend/tmp/code_deploy/` |

## 本地路径

| 项目 | 路径 |
|------|------|
| SSH 密钥 | `E:\Quark\ai.pem` |
| 前端源码 | `E:\java_demo\Ai-scene\AI-backend\AI-frontend\` |
| 后端源码 | `https://github.com/mango-pie/myWebsite`（clone 到 `/tmp` 构建） |

---

## 7. 密码速查

| 用途 | 用户名 | 密码 |
|------|--------|------|
| MySQL root | `root` | `aiscene2026!` |
| MySQL 应用 | `mango` | `571499@` |
| 网站登录 | `mango` | `12345678` |
| 网站管理员 | `scene` | `12345678` |

**加密方式**：`MD5("yupi" + 明文密码)`

---

## 8. 🚀 更新前端

```bash
# 1. 本地改代码
#    目录：E:\java_demo\Ai-scene\AI-backend\AI-frontend\

# 2. 确认 .env.production 关键配置
#    VITE_API_BASE_URL=/api          ← 必须是 /api，不能是 http://IP:8123/api
#    VITE_DEPLOY_BASE_URL=http://121.43.177.236

# 3. 本地构建
cd E:\java_demo\Ai-scene\AI-backend\AI-frontend
npm run build-only

# 4. 上传
scp -i "E:/Quark/ai.pem" -o StrictHostKeyChecking=no -r dist/* root@121.43.177.236:/var/www/ai-frontend/

# 5. 刷新浏览器即可（无需重启服务）
```

## 9. 🚀 更新后端

```bash
# 1. 拉取 + 本地构建
cd /tmp && rm -rf myWebsite
git clone https://github.com/mango-pie/myWebsite.git /tmp/myWebsite
cd /tmp/myWebsite
mvn clean package -DskipTests -q

# 2. 上传
scp -i "E:/Quark/ai.pem" -o StrictHostKeyChecking=no target/AI-0.0.1-SNAPSHOT.jar root@121.43.177.236:/opt/ai-backend/

# 3. 重启后端
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 "
    systemctl stop ai-backend
    chown www-data:www-data /opt/ai-backend/AI-0.0.1-SNAPSHOT.jar
    systemctl start ai-backend
    sleep 10
    curl -s -o /dev/null -w 'HTTP %{http_code}' http://127.0.0.1:8123/api/v3/api-docs
"
# 返回 HTTP 200 即成功
```

## 10. 🚀 修改环境变量

```bash
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 "nano /etc/ai-backend.env"
# 改完后重启
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 "systemctl restart ai-backend"
```

## 11. 🚀 数据库操作

```bash
# 导出
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 \
    "mysqldump -u root -paiscene2026! aiscene > /tmp/aiscene_backup.sql"
scp -i "E:/Quark/ai.pem" root@121.43.177.236:/tmp/aiscene_backup.sql .

# 导入
scp -i "E:/Quark/ai.pem" scene.sql root@121.43.177.236:/tmp/
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 \
    "mysql -u root -paiscene2026! aiscene < /tmp/scene.sql"
```

---

## 12. 🚫 绝对不能做的事

| 禁止 | 原因 | 正确做法 |
|------|------|----------|
| 服务器上 `npm run build` | 内存不足会 OOM | **本地构建，SCP 上传** |
| 服务器上 `mvn package` | 同上 | **本地构建，SCP 上传** |
| 开放 8123 到公网 | 后端裸奔 | 走 Nginx `/api/` 反代 |
| 开放 3306 到公网 | MySQL 暴露 | 仅 127.0.0.1 |
| `.env.production` 设 `http://IP:8123/api` | 浏览器直连被封端口 | 必须用 `/api` |
| `kill -9` 杀 Java | 可能损坏 DB | `systemctl stop ai-backend` |

---

## 13. 🔧 故障排查

### 网站 502/504
```bash
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 "
    systemctl status ai-backend | head -10
    journalctl -u ai-backend --no-pager -n 30 | grep -i error
    ss -tlnp | grep 8123
"
```

### 登录失败
```bash
# 检查 user 表
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 \
    "mysql -u root -paiscene2026! -e 'SELECT id, userAccount FROM aiscene.user;'"

# 重置 mango 密码为 12345678
ssh -i "E:/Quark/ai.pem" root@121.43.177.236 \
    "mysql -u root -paiscene2026! -e \"
        UPDATE aiscene.user
        SET userPassword = MD5(CONCAT('yupi', '12345678'))
        WHERE userAccount = 'mango';
    \""
```

### 服务管理
```bash
systemctl stop|start|restart ai-backend   # 后端
systemctl reload nginx                    # Nginx 重载
journalctl -u ai-backend -f               # 实时日志
```

---

*最后更新：2026-06-20*
