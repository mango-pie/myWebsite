# 部署指南（后端）

> **完整服务器部署流程**（Ubuntu + IP 访问 + 2GB 内存）请参阅：  
> **[docs/DEPLOY_SERVER.md](docs/DEPLOY_SERVER.md)**  
> 该文档面向 Claude Code / 自动化部署，含逐步命令、Nginx、systemd、验收与排错。

仓库：[mango-pie/myWebsite](https://github.com/mango-pie/myWebsite)  
前端仓库：[mango-pie/MyWebsite-ui](https://github.com/mango-pie/MyWebsite-ui)

## 快速链接

| 资源 | 路径 |
|------|------|
| 完整部署手册 | [docs/DEPLOY_SERVER.md](docs/DEPLOY_SERVER.md) |
| **2GB 小机器配置** | [docs/DEPLOY_LOWMEM.md](docs/DEPLOY_LOWMEM.md) |
| Nginx 配置模板 | [deploy/nginx/ai-site.conf](deploy/nginx/ai-site.conf) |
| systemd 后端 | [deploy/systemd/ai-backend.service](deploy/systemd/ai-backend.service) |
| systemd 网易云（可选） | [deploy/systemd/netease-api.service](deploy/systemd/netease-api.service) |
| 环境变量模板 | [deploy/env/ai-backend.env.example](deploy/env/ai-backend.env.example) |
| SQL 脚本 | [src/main/resources/sql/](src/main/resources/sql/) |

运维可观测 Phase A～D 需额外执行（若尚未建表）：

- `src/main/resources/sql/ai_usage_log_schema.sql`
- `src/main/resources/sql/ops_audit_log_schema.sql`
- `src/main/resources/sql/biz_stat_daily_schema.sql`
- `src/main/resources/sql/http_access_log_schema.sql`

联调文档：[`docs/ops-observability/`](docs/ops-observability/README.md) 下 `FRONTEND_SYNC_A`～`D`。

## 本地开发

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 编辑 application-local.yml 填入 API Key
./mvnw.cmd spring-boot:run   # Windows
# 或 mvn spring-boot:run
```

## 构建 JAR

```bash
./mvnw clean package -DskipTests
# 产物：target/AI-0.0.1-SNAPSHOT.jar
```

GitHub Actions 推送 `main` 后自动构建，Artifact 名 `ai-backend-jar`（保留 30 天）。

## 生产启动（摘要）

2GB 机器请用 `--spring.profiles.active=prod`（已关 Redis / 本机 pgvector / MinIO / TTS / 应用工坊，并限制连接池与 Tomcat 线程）。

```bash
java -Xms256m -Xmx512m -jar AI-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

把 `deploy/env/ai-backend.env.example` 同步到 `/etc/ai-backend.env`：至少设置 `APP_REDIS_MODE=off`。精读不要关 `APP_MODULES_KNOWLEDGE`（笔记在本库；向量库用 `KNOWLEDGE_VECTOR_ENABLED=false`）。

环境变量见 `deploy/env/ai-backend.env.example`，详细步骤见 [docs/DEPLOY_SERVER.md](docs/DEPLOY_SERVER.md)。
