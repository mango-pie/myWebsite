# Ai-Backend（个人站点后端）

基于 **Spring Boot 3 / Java 17 / Maven** 的**单体可插拔**后端：平台能力常驻，业务能力通过 `app.modules.*` 运行时开关启停。  
**鉴权为 HttpSession（Cookie）**，不是 JWT；**不是微服务拆分**（见模块解耦基线）。

| 项 | 约定 |
| --- | --- |
| Java | **17** |
| 构建 | **Maven**（`./mvnw` 或本机 `mvn`） |
| 默认端口 | **8123**（`SERVER_PORT`） |
| Context path | **`/api`** |
| 鉴权 | **Session**：登录后 `HttpSession` 属性 `user_login`；可用 Redis Session（探测失败则回落） |
| 模块开关 | **`app.modules.*`**（见下方） |

权威设计与运维文档（请以这些为准，勿与历史 Knowledge AI 规划稿中的 JWT/微服务表述混淆）：

- [模块解耦设计清单](docs/module-decoupling/00-design-checklist.md)
- [模块解耦 · 前端对接](docs/module-decoupling/01-frontend-integration.md)
- [模块 → Schema 映射](src/main/resources/sql/README-modules.md)
- [文档目录总入口](docs/README.md)
- [服务器部署](docs/DEPLOY_SERVER.md)（可选深入）

---

## 快速开始

### 1. 环境

- JDK **17+**
- Maven 3.9+（或使用仓库自带 `mvnw` / `mvnw.cmd`）
- MySQL 5.7+（库名默认 `aiscene`）
- Redis 可选（默认配置里 Session `store-type` 可为 `none`；有 Redis 时用于 Session/缓存）

密钥与外部 AI Base URL 请用环境变量或本地 `application-local.yml` 覆盖，**不要把真实 Key 写进仓库**。

### 2. 配置要点

`src/main/resources/application.yml`（节选，与现状一致）：

```yaml
server:
  port: ${SERVER_PORT:8123}
  servlet:
    context-path: /api

app:
  modules:
    ops: true
    blog: true
    knowledge: true
    reading: true
    chat: true
    study: true
    diary: true
    tts: true
    app-lab: true
```

数据源 / Redis 常用环境变量：`DB_HOST`、`DB_PORT`、`DB_USER`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`（默认 **6380**）。

### 3. 数据库

按启用的模块执行对应 schema，**关闭模块不必删表**。清单见：

[`src/main/resources/sql/README-modules.md`](src/main/resources/sql/README-modules.md)

最小理解：platform（`user` / 站点设置等）始终需要；业务表随 `app.modules.*` 选择执行。

### 4. 编译

```bash
./mvnw clean package -DskipTests
# Windows:
mvnw.cmd clean package -DskipTests
```

产物：`target/AI-0.0.1-SNAPSHOT.jar`（`pom.xml` 中 `artifactId=AI`）。

### 5. 启动 / 停止

```bash
java -jar target/AI-0.0.1-SNAPSHOT.jar
# 或指定开关，例如只开博客与运维：
java -jar target/AI-0.0.1-SNAPSHOT.jar \
  --app.modules.knowledge=false \
  --app.modules.reading=false \
  --app.modules.chat=false \
  --app.modules.study=false \
  --app.modules.diary=false \
  --app.modules.tts=false \
  --app.modules.app-lab=false
```

停止：向进程发 SIGTERM / Ctrl+C，或结束对应 Java 进程。

健康检查（context 已含 `/api`）：`http://localhost:8123/api/actuator/health`  
API 文档（Knife4j / springdoc）：`http://localhost:8123/api/swagger-ui.html`  
（若开启 Knife4j 增强页，也可试 `/api/doc.html`）

---

## 模块开关（`app.modules.*`）

策略：**Feature Flag + 条件装配 + SPI**。关闭某模块后：

- 对应 Controller / 多数 Bean / Mapper **不注册**
- 相关 API 表现为 **404**（或文档约定的业务码），不应 NPE
- 跨模块能力走 SPI；对端关闭时明确报错或 NoOp，不强依赖对方表

| Key | 含义（摘要） |
| --- | --- |
| `ops` | 运维可观测（用量 / 审计 / 统计 / HTTP 日志）；关则 NoOp |
| `blog` | 博客 |
| `knowledge` | 知识库 / RAG / 笔记等 |
| `reading` | 精读异步任务队列（常与 knowledge 同开） |
| `chat` | 对话 / Agent |
| `study` | 学习任务 |
| `diary` | 日记 |
| `tts` | 语音合成 |
| `app-lab` | AI 应用/代码实验台（配置里写作 `app-lab`） |

平台不可关：用户与 Session 鉴权、站点设置、上传、集成探测骨架等。

运行时探测（前端菜单隐藏）：

```http
GET /api/app/modules
```

无需登录即可调用。详情：[前端对接文档](docs/module-decoupling/01-frontend-integration.md)。

YAML、JVM 参数、环境变量均可覆盖开关（Spring 松散绑定，如 `APP_MODULES_BLOG=false`）。

---

## 鉴权说明（Session）

- 登录接口写入 **HttpSession**（常量 `user_login`），浏览器带 Cookie 访问需登录接口。
- 权限用 `@AuthCheck` + AOP，从 Session 取当前用户；**不是** Bearer JWT 微服务网关模式。
- Redis 可用时可将 Session 外置；不可用时仍可按当前配置以本地 Session 等方式运行（见 `RedisAvailability*` 相关配置逻辑）。

---

## 仓库与文档导航

| 路径 | 用途 |
| --- | --- |
| [`docs/module-decoupling/`](docs/module-decoupling/00-design-checklist.md) | 模块解耦架构基线（权威） |
| [`src/main/resources/sql/README-modules.md`](src/main/resources/sql/README-modules.md) | 模块 ↔ SQL schema |
| [`docs/README.md`](docs/README.md) | 功能文档总目录（精读 / 设置 / 运维等） |
| [`DEPLOY.md`](DEPLOY.md) / [`docs/DEPLOY_SERVER.md`](docs/DEPLOY_SERVER.md) | 部署 |

`docs/00`～`06` 等为历史 Knowledge AI 独立项目规划，其中若出现 JWT / 微服务表述，**不代表本仓库当前实现**。

---

## 技术栈（现状摘要）

- Spring Boot **3.5.x**、MyBatis Flex、MySQL
- Redis + Spring Session（按可用性装配）
- LangChain4j、Knife4j / springdoc
- 单体模块化，非独立部署的微服务集群
