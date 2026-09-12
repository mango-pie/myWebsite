# AI 后端服务

## 项目概述

AI 后端服务是一个基于 Spring Boot 的智能代码生成系统，主要功能是通过 AI 模型根据用户的需求生成代码，并支持代码的保存和部署。系统支持两种代码生成模式：HTML 模式和多文件模式，用户可以根据需求选择合适的生成方式。

## 技术栈

- **后端框架**：Spring Boot 3.5.11
- **数据库**：MySQL
- **ORM**：MyBatis Flex
- **AI 框架**：LangChain4j
- **缓存**：Redis
- **API 文档**：Knife4j (Swagger)
- **构建工具**：Maven
- **Java 版本**：17

## 项目结构

```
AI-backend/
├── src/
│   ├── main/
│   │   ├── java/com/ai/
│   │   │   ├── annotation/      # 自定义注解
│   │   │   ├── aop/            # 切面编程
│   │   │   ├── common/         # 通用类
│   │   │   ├── config/         # 配置类
│   │   │   ├── constant/       # 常量定义
│   │   │   ├── controller/     # 控制器
│   │   │   ├── core/           # 核心功能
│   │   │   ├── exception/      # 异常处理
│   │   │   ├── generator/      # 代码生成器
│   │   │   ├── mapper/         # 数据访问层
│   │   │   ├── model/          # 数据模型
│   │   │   ├── saver/          # 代码保存器
│   │   │   ├── service/        # 业务逻辑层
│   │   │   └── AiApplication.java  # 应用入口
│   │   └── resources/
│   │       ├── mapper/         # MyBatis XML映射文件
│   │       ├── prompt/         # AI 提示词模板
│   │       └── application.yml # 应用配置文件
│   └── test/                   # 测试代码
├── .gitignore
├── mvnw
├── mvnw.cmd
└── pom.xml                    # Maven 依赖配置
```

## 核心功能

### 1. 用户管理

- 用户注册、登录
- 用户信息管理
- 用户权限控制

### 2. 应用管理

- 创建应用（设置应用名称、初始化提示词等）
- 编辑应用信息
- 删除应用
- 查看应用列表（个人应用、推荐应用）

### 3. AI 代码生成

- HTML 模式：生成单页 HTML 代码
- 多文件模式：生成包含多个文件的项目
- 流式响应：实时返回生成的代码

### 4. 对话历史管理

- 记录用户与 AI 的对话历史
- 支持查看对话历史
- 支持删除对话历史

### 5. 代码部署

- 将生成的代码部署到服务器
- 生成可访问的部署链接

## 配置说明

### 数据库配置

在 `application.yml` 文件中配置数据库连接信息：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/aiscene
    username: root
    password: root
```

### Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6381
      password:
      ttl: 3600
  session:
    store-type: redis
    timeout: 3600
```

### AI 配置

```yaml
langchain4j:
  open-ai:
    streaming-chat-model:
      base-url: https://api.deepseek.com
      api-key: sk-d693c15ca50c4df7a256a27651ade1f6
      model-name: deepseek-chat
      max-tokens: 8192
      log-requests: true
      log-responses: true
```

### 服务器配置

```yaml
server:
  port: 8123
  servlet:
    context-path: /api
    session:
      cookie:
        max-age: 3600
```

## 部署指南

### 1. 环境准备

- JDK 17 或以上
- MySQL 5.7 或以上
- Redis 6.0 或以上

### 2. 数据库初始化

创建名为 `aiscene` 的数据库，系统会自动创建所需的表结构。

### 3. 构建项目

```bash
mvn clean package
```

### 4. 运行项目

```bash
java -jar target/AI-0.0.1-SNAPSHOT.jar
```

### 5. 访问 API 文档

启动项目后，可通过以下地址访问 API 文档：

```
http://localhost:8123/api/swagger-ui.html
```

## API 文档

### 主要 API 端点

#### 用户管理

- `POST /api/user/register` - 用户注册
- `POST /api/user/login` - 用户登录
- `GET /api/user/me` - 获取当前用户信息
- `PUT /api/user` - 更新用户信息

#### 应用管理

- `POST /api/app` - 创建应用
- `GET /api/app` - 获取应用列表
- `GET /api/app/{id}` - 获取应用详情
- `PUT /api/app` - 更新应用
- `DELETE /api/app/{id}` - 删除应用
- `POST /api/app/chat` - 与 AI 对话生成代码
- `POST /api/app/deploy` - 部署应用

#### 对话历史管理

- `GET /api/chat-history` - 获取对话历史
- `DELETE /api/chat-history/{id}` - 删除对话历史

## 核心模块说明

### 1. AI 代码生成模块

- **AiCodeGeneratorFacade**：AI 代码生成的核心类，负责协调代码生成和保存流程
- **CodeParser**：解析 AI 生成的代码，提取有用信息
- **CodeFileSaver**：将生成的代码保存到文件系统

### 2. 应用管理模块

- **AppService**：应用管理的业务逻辑
- **AppController**：处理应用相关的 HTTP 请求
- **AppMapper**：应用数据的访问层

### 3. 用户管理模块

- **UserService**：用户管理的业务逻辑
- **UserController**：处理用户相关的 HTTP 请求
- **UserMapper**：用户数据的访问层

### 4. 对话历史模块

- **ChatHistoryService**：对话历史管理的业务逻辑
- **ChatHistoryController**：处理对话历史相关的 HTTP 请求
- **ChatHistoryMapper**：对话历史数据的访问层


