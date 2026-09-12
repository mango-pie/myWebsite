# 项目管理与发布规范

> 目标：让「更新」变成一件**可预测、可验证、可回滚**的事，而不是靠记忆和手工操作。
> 本文档配合本次引入的基础设施使用：Flyway 数据库迁移、Redis 探测修复、Actuator 健康检查、
> 一键部署脚本、CI 门禁、docker-compose 预演。

---

## 0. 一图看懂发布流程

```
功能分支 feature/xxx
   │  push
   ▼
GitHub CI（mvn verify：编译 + 单元测试）  ──✗──► 修复后再推
   │  ✓ 绿灯
   ▼
提 PR → 合入 main
   │
   ▼
main CI 构建 JAR（build-backend.yml，产物 ai-backend-jar）
   │  下载产物
   ▼
（可选但推荐）本地 docker-compose 预演升级：用 prod 数据副本跑一遍
   │  ✓
   ▼
服务器执行 deploy.sh：备份 → 替换 JAR → 启动(自动 Flyway 迁移) → 健康检查 → 失败自动回滚
   │  ✓
   ▼
线上验收：/api/actuator/health、/api/actuator/info
```

---

## 1. 分支与协作（P2-7）

### 分支模型（轻量，适合个人/小团队）
- `main`：**始终可发布**。只允许通过 PR 合入，不直接 push。
- `feature/xxx`：功能开发分支，从 `main` 切出。
- `fix/xxx`：线上问题修复分支。
- 发布用 tag 标记（见第 2 节），不再单独拉 release 分支。

### 在 GitHub 上开启 main 分支保护（一次性设置）
仓库 → Settings → Branches → Add branch ruleset（或 Add rule）：
1. Branch name pattern：`main`
2. 勾选 **Require a pull request before merging**
3. 勾选 **Require status checks to pass before merging** → 选择 `CI / verify`
4. 勾选 **Require branches to be up to date before merging**
5.（可选）**Do not allow bypassing the above settings**

设置后，任何改动都必须走 PR 且 CI 绿灯才能进 `main`，从源头避免「随手一提就翻车」。

### 日常提交建议
- 一个 PR 只做一件事，改动尽量小、可 review。
- commit message 用祈使句：`fix: 修复 Redis 探测在端口被占用时卡死`。

---

## 2. 版本与标记（P1-4）

### 版本号（语义化）
`主版本.次版本.修订号`，例如 `v1.3.0`：
- 主版本：不兼容的大改（如数据库结构大变、接口不兼容）
- 次版本：新增功能，向后兼容
- 修订号：bug 修复

### 每次发布打 tag
```bash
git checkout main && git pull
git tag -a v1.3.0 -m "本次发布内容摘要"
git push origin v1.3.0
```

### 产物携带版本信息
已通过 `spring-boot-maven-plugin` 的 `build-info` 目标生成 `build-info.properties`，
运行后可查询：
```bash
curl http://127.0.0.1:8123/api/actuator/info
# => {"build":{"version":"0.0.1-SNAPSHOT","time":"...","artifact":"AI",...}}
```
> 建议：正式发布前把 `pom.xml` 的 `<version>` 从 `0.0.1-SNAPSHOT` 改为与 tag 一致（如 `1.3.0`），
> 这样 `/actuator/info` 就能明确显示线上跑的是哪个版本。

---

## 3. 数据库迁移（Flyway，P0-1）

### 核心变化
- 迁移脚本位于 `src/main/resources/db/migration/`，命名 `V<序号>__<描述>.sql`。
- **应用启动时自动执行迁移**，无需再手工按顺序跑 SQL，也不可能重复跑或跑错脚本。
- 迁移记录存于数据库 `flyway_schema_history` 表。

### 为什么对「存量生产库」是安全的
- 所有迁移均为 `CREATE TABLE IF NOT EXISTS` / 幂等 `ALTER`（通过 information_schema 判断），**不含任何 DROP**。
- 配置了 `baseline-on-migrate: true` + `baseline-version: 0`：
  - 存量库首次启动 → 标记基线为 0 → 依次执行 V1+，已存在的表自动跳过，只补齐缺失的表/列。
  - 全新空库 → 从 V1 建全套结构。

### 新增一次数据库变更的正确姿势
1. 在 `db/migration/` 新建下一个版本号文件，例如 `V15__add_xxx.sql`。
2. 只写增量、幂等的语句（`CREATE TABLE IF NOT EXISTS` / 带 information_schema 判断的 `ALTER`）。
3. **绝不修改已发布过的迁移文件**（Flyway 会校验 checksum，改了会启动失败）。
4. 本地用 docker-compose 预演（第 5 节）确认迁移成功。

### 出问题时的逃生阀
若某次迁移导致启动失败，临时用 `FLYWAY_ENABLED=false` 启动应用先恢复服务，再排查迁移脚本：
```bash
FLYWAY_ENABLED=false systemctl restart ai-backend   # 或在 env 文件中设置后重启
```

---

## 4. 运行时健壮性（P0-2 / P0-3）

- **Redis 探测修复**：启动探测改为真正发送 RESP `PING` 并要求 `+PONG`，
  端口被别的服务占用（如 VMware NAT）也能正确判为不可用并降级为内存 Session，
  不再出现「获取 Redis 连接无限阻塞 → 线程池耗尽 → 全站卡死」。
  同时给 Redis 客户端加了 `timeout` / `connect-timeout`。
- **启动自检**：应用就绪后在日志打印一段「启动配置自检」，包含 profile、数据源、
  Redis 可用性、Session 存储、Flyway、启用的模块。部署后先看这段日志即可确认「实际加载了什么」。

---

## 5. 上线前本地预演（docker-compose，P2-9）

强烈建议每次涉及数据库变更的发布，先在本地用 **prod 数据副本**跑一遍：

```bash
# 1. 导出生产库（在服务器上）
mysqldump -u aiscene -p aiscene > aiscene.sql
# 2. 放到预演目录
mkdir -p deploy/rehearsal/dump && cp aiscene.sql deploy/rehearsal/dump/
# 3. 起 MySQL + Redis（首次会自动导入 dump）
docker compose up -d mysql redis
# 4. 构建 JAR
./mvnw -DskipTests package
# 5. 起应用（自动执行 Flyway 迁移）
docker compose --profile full up app
# 6. 验证
curl http://127.0.0.1:8123/api/actuator/health   # 期望 {"status":"UP"}
```
若这里迁移或启动失败，就说明生产也会失败——在本地修好再上线。

清理：`docker compose down -v`（`-v` 会删除预演数据卷）。

---

## 6. 正式部署（deploy.sh，P1-5）

在服务器上执行（脚本会自动备份、迁移、健康检查、失败回滚）：
```bash
# 假设新 JAR 已上传到服务器 ~/AI-0.0.1-SNAPSHOT.jar
sudo bash deploy/deploy.sh ~/AI-0.0.1-SNAPSHOT.jar
```
脚本流程：
1. 备份数据库到 `/opt/ai-backend/backups/db_aiscene_<时间戳>.sql`
2. 备份当前 JAR
3. 停服务 → 替换 JAR → 启动（启动时 Flyway 自动迁移）
4. 轮询 `/api/actuator/health` 直到 200（默认最多 30 次 × 3s）
5. **失败自动回滚旧 JAR 并重启**（数据库迁移不自动回滚，脚本会提示用备份恢复）

可用环境变量覆盖：`APP_ROOT` `JAR_NAME` `SERVICE` `ENV_FILE` `DB_NAME` `HEALTH_URL` 等。

---

## 7. 回滚

- **应用回滚**：`cp <备份JAR> /opt/ai-backend/AI-0.0.1-SNAPSHOT.jar && systemctl restart ai-backend`
- **数据库回滚**：用当次备份 `db_aiscene_<时间戳>.sql` 恢复。因此发布前的自动备份非常关键。
- 由于所有迁移都是「加法」（不删表不删列），绝大多数情况**只回滚应用 JAR 即可**，旧代码能兼容多出来的表/列。

---

## 8. 发布检查清单（每次照做）

- [ ] 功能分支 CI 绿灯
- [ ] PR 已 review 并合入 main
- [ ] 如有 DB 变更：已新增 `V<n>__` 迁移文件，且**未改动历史迁移**
- [ ] 本地 docker-compose 用 prod 数据副本预演通过
- [ ] `pom.xml` 版本号已更新，已打并推送 git tag
- [ ] 服务器执行 `deploy.sh`，健康检查通过
- [ ] `/api/actuator/info` 显示的版本符合预期
- [ ] 关键页面/接口人工抽查正常

---

## 9. 后续可继续加强（非阻塞）
- 用 Testcontainers 写真正的集成冒烟测试（起临时 MySQL+Redis 跑 `@Tag("integration")`）。
- 接入简单告警（启动失败 / 5xx 突增）。
- 维护 `CHANGELOG.md`，每个 tag 记录变更。
