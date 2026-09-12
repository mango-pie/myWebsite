# 交接：`modules` 设置模块（业务模块开关运行时化）

> 来源：前端「站点设置 → 业务模块开关」页面已实现（AI-frontend `src/pages/admin/SettingsPage.vue`），
> 完全走现有通用设置 API；后端只差**一个设置模块实现类**。本文冻结契约与实现要点。
> 分支基线：`feature/module-decoupling`。预计工作量：**1 个新文件 + 1 处读取链改动**。

## 一、前端已依赖的契约（冻结，勿改字段名）

| 项 | 值 |
|---|---|
| 设置模块 code | `modules` |
| displayName | `业务模块开关` |
| writable | `true` |
| schema 字段 valueType | 全部 `bool` |
| schema 字段 key | **必须与 `ModuleEnablementService.snapshot()` 的 key 完全一致**：<br>`ops` `blog` `knowledge` `reading` `chat` `study` `diary` `worklog` `tts` `app-lab` |
| 读取 | `GET /admin/site-settings/modules/{schema,""}`（已有） |
| 写入 | `PUT /admin/site-settings/modules`，body `{items: {<key>: boolean}}`，**只提交变更项**（前端已如此） |
| 重置 | `POST /admin/site-settings/modules/reset`（已有） |
| 生效查询 | `GET /app/modules` 必须反映 DB 覆盖后的最终状态 |

前端行为约定：`listSiteSettingModules()` 返回中含 code=`modules` 且 `writable=true` 时，页面自动进入可编辑模式；
否则显示只读降级视图。因此**上线顺序无关紧要**：先发前端是安全的（只读），后端跟上即点亮。

## 二、实现要点（按现有模式）

### 1. 新增 `ModulesSettingModule`（参照 `AppModule.java` 写法）

```java
package com.ai.setting.module;

// 注意：不要加 @ConditionalOnModule！
// 模块开关的设置模块必须无条件注册——否则某模块关闭后，连重新打开它的开关都消失了。
@Component
public class ModulesSettingModule implements SettingModule {

    @Override
    public String code() { return "modules"; }   // SiteSettingConstant 增加 MODULE_MODULES = "modules"

    @Override
    public String displayName() { return "业务模块开关"; }

    @Override
    public String phase() { return "P1"; }

    @Override
    public List<SettingFieldSchema> schema() {
        // key 顺序对齐 ModuleEnablementService.snapshot()；label 为前台展示名
        return List.of(
                bool("ops", "运维中心"),
                bool("blog", "博客"),
                bool("knowledge", "知识库"),
                bool("reading", "精读工作台"),
                bool("chat", "对话"),
                bool("study", "学习"),
                bool("diary", "日记"),
                bool("worklog", "工作手账"),
                bool("tts", "语音"),
                bool("app-lab", "应用工坊"));
    }
    // defaults() 遍历 schema；validate() 校验值为 boolean 即可
}
```

### 2. `ModuleEnablementService` 读取链改为「DB 覆盖优先」

```java
public boolean isEnabled(String moduleName) {
    // 1) DB 覆盖：site_setting 中 (module='modules', key=moduleName) 存在记录时以其为准
    // 2) 无记录 → 回落 AppModuleProperties（yml / APP_MODULES_* 环境变量）
    //
    // ★ 关键语义：缺行 = 未覆盖 = 用部署默认值。绝不能把「无记录」当成 false，
    //   否则上线瞬间全站模块被清空。
    Boolean overridden = siteSettingService.getModuleSwitch(moduleName); // 新增查询，null=未覆盖
    return overridden != null ? overridden : appModuleProperties.isEnabled(moduleName);
}
```

`snapshot()` 与 `toVo()` 无需改结构——它们调用 `isEnabled` 即自动拿到覆盖后状态，
`GET /app/modules` 与各处 `@ConditionalOnModule` 门禁随之生效。

### 3. 缓存与生效时机

- `site_setting` 走 `SiteSettingCache`（TTL 300s）。确认 `update`/`reset` 路径已有缓存失效；
  若没有，`PUT /admin/site-settings/modules` 后开关最长延迟 5 分钟生效——前端会把这种状态
  标为「未生效」，但体验要求 update 后 evict。
- `app.redis.mode=off`（当前生产）时该缓存是否仍生效，请后端顺手确认（Caffeine/无缓存两种形态）。

### 4. 已经由通用链路覆盖、无需额外做的

- 管理员鉴权：`AdminSiteSettingsController` 各端点已有 `@AuthCheck(ADMIN_ROLE)`
- 审计：写操作自动落 `site_setting_audit`（`GET /admin/site-settings/audit` 可查）
- 维护模式：`/admin/site-settings` 在 `MaintenanceModeInterceptor` 白名单中
- 前端保存后的即时性：页面保存成功后重拉 `GET /app/modules` 并调 capabilities `load()`，
  导航/路由门禁（`moduleGate`）立即反映

## 四、后端实现回执（2026-09-11 已实现，73/73 单测通过）

改动清单（比文档预估多 1 处接口扩展，其余一致）：

| 文件 | 改动 |
|---|---|
| `constant/SiteSettingConstant.java` | 新增 `MODULE_MODULES = "modules"` |
| `setting/module/ModulesSettingModule.java` | **新增**，无 `@ConditionalOnModule` |
| `service/SiteSettingService.java` + `impl/SiteSettingServiceImpl.java` | 新增 `getModuleSwitch`（单 key）与 `moduleSwitchOverrides`（批量） |
| `config/ModuleEnablementService.java` | 改为 DB 覆盖优先 |
| `config/AppModuleProperties.java` | `normalizeKey` 由包级私有改 `public`（设置模块 key / DB 覆盖 key / 条件装配共享同一规范） |
| `test/config/ModuleEnablementServiceTest.java` | **新增** 5 个用例 |

**比文档方案强化的两点**（避免实际缺陷）：
1. `ModuleEnablementService.snapshot()` 用一次 `moduleSwitchOverrides()` 批量读覆盖，而非逐 key 调用 `getModuleSwitch`——后者在 Redis 关闭时是 N 次 DB 查询。
2. `ModulesSettingModule.schema()` 的 `defaultValue` 取自 `AppModuleProperties`，而非硬编码 `true`。否则 prod（tts/app-lab 部署默认关）下设置页会把“未覆盖”显示成“已开启”，与 `/app/modules` 直接矛盾。

**文档第 3 问的答案（缓存生效时机）**：`updateModule`/`resetModule` 均已调用 `siteSettingCache.evict(moduleCode)`（`SiteSettingServiceImpl:198/219`），**保存后立即生效，不存在 5 分钟延迟**。`SiteSettingCache` 仅在 `app.redis.available=true` 时启用 Redis；`app.redis.mode=off`（当前 prod）时 `useRedis()` 为 false，缓存退化为直读 DB 的透传，无陈旧问题，仅每次 `/app/modules` 多一次查询（可接受）。

### ★ 必须澄清的生效边界（与文档第 2 节第 2 点的假设不符）

文档称“各处 `@ConditionalOnModule` 门禁随之生效”——**经核实不成立**。两者是两条独立链路：

- `@ConditionalOnModule` → `OnModuleCondition` 在**容器启动时**直接读 `Environment`（`app.modules.*`，即 yml / `APP_MODULES_*` 环境变量），**不经过 `ModuleEnablementService`**，且 Spring 条件装配不会在运行期重新求值。
- `ModuleEnablementService` → 只被 `GET /app/modules` 消费。

因此 DB 开关的**实际生效范围**是：

| 行为 | 关闭模块后是否立即生效 |
|---|---|
| `GET /app/modules` 返回 false | ✅ 立即（这正是文档验收项 2/3/4 覆盖的部分） |
| 前端导航隐藏、路由跳 `module-unavailable` | ✅ 立即（前端基于 `/app/modules` 门禁） |
| 该模块的**后端 API 是否被拒绝** | ❌ 不拦，接口仍可访问 |
| 该模块 **Bean 是否停止装配** | ❌ 不生效，需重启应用 |

即文档验收清单全部可通过（它们都是前端 + `/app/modules` 层面的断言），但“运行时开关”当前**不等同于后端强约束**。若要补齐 API 层门禁，需另加一个请求级 `HandlerInterceptor`（按路径前缀映射模块 → `moduleEnablementService.isEnabled` → 403），这是**独立于本文档范围的架构决策**，涉及路径前缀映射与现有白名单，尚未实施，建议单独评审后再做。

### 验收清单（后端侧可验证项）

- [x] `listSiteSettingModules` 含 `{code:"modules", writable:true}`（`SettingModule` 默认 `writable()=true`，`registry` 自动发现）
- [x] schema 字段 key 与 `snapshot()` 完全一致（单测断言 key 序列相等）
- [x] 缺行回落 yml 默认而非 false（单测 `missingRow_fallsBackToDeploymentDefault_notFalse`）
- [x] DB 覆盖优先于部署默认（单测 `dbOverride_winsOverDeploymentDefault`）
- [x] 重置后回到 yml/env 默认（`resetModule` 物理删除 `module='modules'` 全部行 + evict）
- [x] 非管理员 PUT → 401、审计落 `site_setting_audit`（`AdminSiteSettingsController` 已有 `@AuthCheck` + 通用链路）
- [ ] 需要真实 DB/前端联调的两项：① 关 chat 后无痕窗口行为；② prod（redis 关）下保存后即时生效——留待集成环境

