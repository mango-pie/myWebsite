# 部署指南（后端）

> **完整服务器部署流程**（Ubuntu + IP 访问 + 2GB 内存）请参阅：  
> **[docs/DEPLOY_SERVER.md](docs/DEPLOY_SERVER.md)**  
> 该文档面向 Claude Code / 自动化部署，含逐步命令、Nginx、systemd、验收与排错。

仓库：[mango-pie/Ai-Backend](https://github.com/mango-pie/Ai-Backend)  
前端仓库：[mango-pie/Ai-frontend](https://github.com/mango-pie/Ai-frontend)

## 快速链接

| 资源 | 路径 |
|------|------|
| 完整部署手册 | [docs/DEPLOY_SERVER.md](docs/DEPLOY_SERVER.md) |
| Nginx 配置模板 | [deploy/nginx/ai-site.conf](deploy/nginx/ai-site.conf) |
| systemd 后端 | [deploy/systemd/ai-backend.service](deploy/systemd/ai-backend.service) |
| systemd 网易云（可选） | [deploy/systemd/netease-api.service](deploy/systemd/netease-api.service) |
| 环境变量模板 | [deploy/env/ai-backend.env.example](deploy/env/ai-backend.env.example) |
| SQL 脚本 | [src/main/resources/sql/](src/main/resources/sql/) |

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

```bash
java -Xms256m -Xmx512m -jar AI-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

环境变量见 `deploy/env/ai-backend.env.example`，详细步骤见 [docs/DEPLOY_SERVER.md](docs/DEPLOY_SERVER.md)。
