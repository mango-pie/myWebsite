# V1 基础后台需求文档

## 1. 阶段目标

V1 的目标是在 1 周左右搭建一个完整的 Spring Boot 3 + Vue3 前后端分离后台系统，完成用户注册、登录、JWT 认证、用户管理、修改密码、头像上传和基础权限控制。

这一阶段不涉及 AI 能力，重点是把项目基础工程搭好，为后续知识库、RAG、聊天模块提供稳定的用户体系和后台框架。

## 2. 技术范围

### 后端

- Spring Boot 3
- Spring Security
- JWT
- MyBatis Plus
- MySQL
- Redis
- MinIO

### 前端

- Vue3
- Vite
- TypeScript
- Pinia
- Vue Router
- Element Plus
- Axios

## 3. 功能清单

### 用户注册

用户可以通过用户名、密码、邮箱等信息注册账号。

注册规则：

- 用户名不能为空
- 用户名不能重复
- 密码不能为空
- 密码需要使用 BCrypt 加密后保存
- 邮箱格式需要合法
- 注册用户默认绑定普通用户角色
- 用户状态默认为启用

### 用户登录

用户输入用户名和密码后，后端通过 Spring Security 完成认证。

登录成功后：

- 生成 JWT Token
- 将 Token 或登录态信息写入 Redis
- 返回用户基本信息、角色和权限标识
- 记录登录日志

登录失败后：

- 返回统一错误响应
- 记录失败原因
- 可选：记录失败登录日志

### JWT 认证

系统采用 Spring Security + JWT 实现无状态认证。

请求认证流程：

```text
前端请求携带 Authorization Token
  |
  v
JWT Filter 拦截请求
  |
  v
解析 Token 获取用户 ID
  |
  v
校验 Redis 登录态
  |
  v
加载用户角色与权限
  |
  v
写入 SecurityContext
  |
  v
访问业务接口
```

### 用户管理

管理员可以进行用户管理。

功能包括：

- 用户列表分页查询
- 按用户名、昵称、状态搜索
- 新增用户
- 编辑用户信息
- 启用用户
- 禁用用户
- 重置用户密码
- 分配用户角色

### 修改密码

登录用户可以修改自己的密码。

修改规则：

- 必须校验旧密码
- 新密码不能与旧密码相同
- 新密码需要加密保存
- 修改成功后建议清除 Redis 中旧 Token，让用户重新登录

### 上传头像

用户可以上传头像。

上传流程：

```text
前端选择头像文件
  |
  v
后端校验文件类型和大小
  |
  v
上传到 MinIO
  |
  v
MySQL 更新用户头像 URL
  |
  v
前端刷新用户信息
```

### 权限管理

V1 阶段权限可以简化实现，采用用户、角色、权限标识的方式。

基础角色：

- `admin`：管理员
- `user`：普通用户

基础权限：

- `system:user:list`
- `system:user:add`
- `system:user:update`
- `system:user:delete`
- `system:role:list`
- `knowledge:base:list`
- `knowledge:document:upload`
- `chat:conversation:create`

## 4. 前端页面需求

### 登录页

页面包含：

- 用户名输入框
- 密码输入框
- 登录按钮
- 跳转注册入口
- 登录失败提示

### 注册页

页面包含：

- 用户名
- 密码
- 确认密码
- 邮箱
- 注册按钮
- 跳转登录入口

### 后台布局

后台采用典型管理端布局：

- 左侧菜单
- 顶部导航
- 用户头像下拉菜单
- 内容区域
- 标签页可选

### 用户管理页

页面包含：

- 搜索表单
- 用户表格
- 新增用户按钮
- 编辑用户弹窗
- 重置密码按钮
- 启用 / 禁用按钮

### 个人中心

页面包含：

- 用户基本信息
- 头像上传
- 修改密码

## 5. 后端接口建议

### 认证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 用户注册 |
| POST | `/api/auth/login` | 用户登录 |
| POST | `/api/auth/logout` | 用户退出 |
| GET | `/api/auth/profile` | 当前用户信息 |

### 用户接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/users` | 用户分页查询 |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users/{id}` | 修改用户 |
| DELETE | `/api/users/{id}` | 删除用户 |
| PUT | `/api/users/{id}/status` | 修改用户状态 |
| PUT | `/api/users/password` | 修改当前用户密码 |
| POST | `/api/users/avatar` | 上传头像 |

## 6. 数据库设计

### user 用户表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| username | varchar | 用户名 |
| password | varchar | 加密密码 |
| nickname | varchar | 昵称 |
| email | varchar | 邮箱 |
| avatar | varchar | 头像地址 |
| status | tinyint | 状态，1 启用，0 禁用 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

### role 角色表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| role_code | varchar | 角色编码 |
| role_name | varchar | 角色名称 |
| description | varchar | 角色描述 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

### user_role 用户角色表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 用户 ID |
| role_id | bigint | 角色 ID |

### login_log 登录日志表

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 用户 ID |
| username | varchar | 用户名 |
| ip | varchar | 登录 IP |
| user_agent | varchar | 浏览器信息 |
| login_status | tinyint | 登录状态 |
| message | varchar | 登录结果说明 |
| login_time | datetime | 登录时间 |

## 7. 后端实现重点

### 统一响应结构

建议封装统一响应对象：

```text
code：业务状态码
message：响应消息
data：响应数据
```

### 全局异常处理

建议使用 `@RestControllerAdvice` 统一处理：

- 参数校验异常
- 认证异常
- 权限异常
- 业务异常
- 系统异常

### 密码安全

密码必须使用 BCrypt 加密，不允许明文存储。

登录时使用 `PasswordEncoder.matches()` 校验密码。

### Token 管理

Access Token 可以设置较短有效期，Redis 登录态可以设置稍长有效期。

退出登录时删除 Redis 中的登录态。

## 8. V1 验收标准

- 用户可以注册账号
- 用户可以登录并获得 JWT Token
- 前端请求可以自动携带 Token
- 未登录访问后台接口会被拦截
- Token 无效或过期会跳转登录页
- 管理员可以查询和管理用户
- 用户可以修改密码
- 用户可以上传头像
- Redis 中可以看到登录态数据
- MySQL 中可以看到用户和登录日志数据

## 9. 面试讲法

V1 阶段可以这样讲：

本项目基础后台采用 Spring Security + JWT 实现前后端分离认证。用户登录成功后，系统生成 JWT Token，并将登录态缓存到 Redis。后续请求通过自定义 JWT 过滤器解析 Token，校验 Redis 登录状态，再将用户权限写入 SecurityContext，从而实现无状态认证和接口权限控制。

如果面试官继续追问，可以补充：

- JWT 本身无状态，服务端无法主动让 Token 失效，所以项目结合 Redis 保存登录态，退出登录或修改密码时可以删除 Redis 中的 Token，实现主动失效。
- 密码使用 BCrypt 单向加密保存，即使数据库泄露，也不会直接暴露用户原始密码。
- 权限设计采用用户、角色、权限标识三层模型，后续可以扩展到菜单权限和按钮权限。
