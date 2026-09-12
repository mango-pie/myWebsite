# P0 重构交接文档：事务边界（P0-2）与调度线程分离（P0-3）

> 来源：2026-09-11 架构深度审查（前端会话产出，供后端会话执行）。
> 分支基线：`feature/module-decoupling`。
> 两个问题相互独立，可分两个 PR；每个都有独立验证方法。

---

## 问题一（P0-3）：单线程调度器被长任务独占

### 现状与证据

- `src/main/java/com/ai/job/KnowledgeReadingJobWorker.java:26`：`@Scheduled(fixedDelay = 3000)` 的
  `pollAndRun()` 在**调度线程上同步执行** `runJob(job)`。`runJob` → `executeSearch`/`executeDistill`
  → `mergeDistill`，内含 Jina 抓取 + 多轮 LLM 蒸馏（单次超时 120s），整个任务可跑数分钟。
- 项目未配置 `spring.task.scheduling.pool.size`，也无自定义 `TaskScheduler` → Spring 默认
  **单线程**调度器。
- 后果：一个精读任务运行期间，`OpsRetentionCleanupJob`（每日 03:30，`OpsRetentionCleanupJob.java:34`）
  无法触发；同一线程上的 `failStuckRunning`/`failStuckQueued` 卡死收割也停摆——
  即"长任务反而关掉了对长任务的自愈"。

### 重构方案（保持"全局单 RUNNING 槽"串行语义不变）

`claimNext()` 的 CAS 认领 + RUNNING 计数检查（`KnowledgeReadingJobServiceImpl.java:256-298`）
本身就是并发安全的：有任务 RUNNING 时返回 null，因此**任意时刻至多一个 job 在执行**，
把它提交到独立单线程池不会引入并发，只会把"执行"从调度线程挪走。

```java
@Slf4j
@ConditionalOnModule({"knowledge", "reading"})
@Component
public class KnowledgeReadingJobWorker implements AutoCloseable {

    private final ExecutorService jobExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "reading-job-worker");
        t.setDaemon(true);
        return t;
    });

    @Scheduled(fixedDelay = 3000)
    public void pollAndRun() {
        try {
            // 认领与收割都是毫秒级 DB 操作，留在调度线程
            knowledgeReadingJobService.failStuckRunning(KnowledgeReadingJobConstant.STUCK_TIMEOUT_MINUTES);
            knowledgeReadingJobService.failStuckQueued(KnowledgeReadingJobConstant.QUEUED_TIMEOUT_MINUTES);
            KnowledgeReadingJob job = knowledgeReadingJobService.claimNext();
            if (job == null) {
                return;
            }
            jobExecutor.execute(() -> runJob(job));
        } catch (Exception e) {
            log.error("Knowledge reading job worker failed", e);
        }
    }

    // runJob(...) 原样不动

    @Override
    @PreDestroy
    public void close() {
        jobExecutor.shutdownNow();
        // 中断后任务可能停在半路：重启后由 failStuckRunning(STUCK_TIMEOUT_MINUTES=20) 收割，
        // 与现状"进程被 kill"的行为一致，无需额外补偿。
    }
}
```

配套：`application.yml` 增加

```yaml
spring:
  task:
    scheduling:
      pool:
        size: 2   # worker 轮询与 OpsRetentionCleanupJob 互不阻塞
```

### 验证

1. 提交一个多 URL 精读任务，任务运行期间手动观察 `OpsRetentionCleanupJob` 不再被饿死
   （临时把 cron 调到 1 分钟后验证，验证完改回）。
2. 任务失败/成功路径回归：`markFailed`/`markSuccess`/SSE 通知（`ReadingJobNotifier`）行为不变。
3. `AiUsageCallContext.setJobId/clearJob`（`executeDistill` 内）在同一 worker 线程上成对调用，
   迁线程后 ThreadLocal 语义不受影响——确认 usage log 的 job_id 仍正确写入。

---

## 问题二（P0-2）：事务边界内执行外部 HTTP/LLM 调用

### 现状与证据（生产连接池仅 5：`application-prod.yml` hikari.maximum-pool-size）

| 位置 | 事务内外部调用 | 耗时量级 |
|---|---|---|
| `KnowledgeReadingJobServiceImpl.submit`（:116-161，`@Transactional`） | `deepSeekBalanceGuard.assertCanIngest()`（:123-125，HTTP）+ `buildOutline()`（:153，LLM 最长 120s） | 秒~分钟 |
| `KnowledgeReadingJobServiceImpl.submitSearch`（:76-114，`@Transactional`） | `assertCanIngest()`（:82-84） | 秒级 |
| `KnowledgeMergeDistillServiceImpl.mergeDistill`（:73-165，`@Transactional`） | `materialize(urls)`（:99，逐 URL Jina 抓取）+ 分块蒸馏循环（:140-144，每块一次 LLM 调用） | 分钟级 |

Spring 事务从方法入口即持有连接直到返回：4~5 个并发提交即可耗尽 5 个连接，
**全站所有接口（含 `/actuator/health`，进而 deploy.sh 的健康检查/回滚）排队超时**。

### 方案 A：submit / submitSearch / continueFromSearch（重排"先生成大纲，后落库"）

> **关键语义坑，必须按此顺序重排**：原实现里 job 行以 `PENDING/QUEUED` 插入、随后在**同一未提交
> 事务内**调 LLM 生成大纲并 `markAwaitingOutline`。若简单去掉 `@Transactional` 而保持"先插行后调
> LLM"，job 行立即对外可见，worker 会在大纲生成的最长 120s 窗口内把任务认领走并直接蒸馏
> ——绕过用户的大纲确认，这不是边界情况而是必然发生的竞态。
> 因此：**外部调用必须全部前置到任何写库之前**。`buildOutline` 自身 catch 全部异常返回占位大纲
> （:818-839），前置后不会让流程卡死。

```java
@Override
public KnowledgeReadingJobVO submit(KnowledgeIngestBatchUrlRequest request, Long userId) {
    List<String> urls = normalizeUrls(request);
    if (request.getJobId() != null) {
        return continueFromSearch(request, urls, userId);
    }
    // —— 全部外部调用与校验，均不持连接 ——
    if (readingRuntimeSettings.balanceCheckEnabled()) {
        deepSeekBalanceGuard.assertCanIngest();          // HTTP，事务外
    }
    assertQueueCapacity(userId);

    KnowledgeIngestBatchUrlRequest distill = copyDistillRequest(request, urls);
    // ... 组装 snapshot / job（原样）...

    // 大纲确认步：LLM 调用前置到落库之前，防止 job 以 QUEUED 状态被 worker 提前认领
    String outline = null;
    if (Boolean.TRUE.equals(distill.getOutlineConfirm())) {
        String goal = StrUtil.blankToDefault(distill.getAgentQuery(),
                distill.getUrls() == null ? "" : "多来源合蒸");
        outline = buildOutline(goal, null);              // LLM，事务外、insert 之前
        job.setStatus(KnowledgeReadingJobConstant.STATUS_WAITING);
        job.setProgress(KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
        job.setResultJson(writeJson(outline));
    }
    knowledgeReadingJobMapper.insert(job);               // 单条语句，自提交

    if (outline != null) {
        readingJobNotifier.notify(userId, job.getId(),
                KnowledgeReadingJobConstant.STATUS_WAITING,
                KnowledgeReadingJobConstant.PROGRESS_AWAITING_OUTLINE);
    }
    // ... VO 组装（outlineConfirm 分支同原逻辑）...
}
```

- `submitSearch`：去掉 `@Transactional`，`assertCanIngest()` 天然移到 insert 前；其余只有一条
  insert，语义不变。
- `continueFromSearch`：同 submit 重排——`buildOutline` 前置；`job` 行一次性 update 到终态
  （outlineConfirm ? WAITING/AWAITING_OUTLINE+resultJson : PENDING/QUEUED），再 notify。
  原 `markAwaitingOutline`（:334-350）在 submit/continue 中的调用点被上面的"一次到位写 + notify"
  取代；该方法本身保留（接口方法，不删）。
- **语义等价性**：
  - 余额不足/排队满 → 无行插入（原：回滚，观察者视角相同）；
  - outlineConfirm → 任务以 WAITING 落库，worker 的 claimNext 查询条件
    `status=PENDING and progress in (SEARCHING,QUEUED)` 不会命中它（与原回滚后再插 WAITING 等价）；
  - 进程在 insert 后任意点崩溃 → 原实现丢失整个 job；新实现可能留下一个 WAITING（等人确认）
    或 PENDING 的 job——均为合法可恢复状态，且有 QUEUED 30 分钟超时收割兜底。

### 方案 B：mergeDistill（补偿式：外部调用不持连接，失败补偿清理）

`mergeDistill` 的持久化只有三处：`ingestionService.ingestAgentResult`（:136，写 source_document）、
`createNote`（:145，insert knowledge_note）、alignment update（:155）。原大事务的意义是
"distill 失败时把已写的 source_document 一并回滚"。重构：

1. 去掉 `mergeDistill` 两个重载的 `@Transactional`。
2. `materialize` + 蒸馏循环照旧执行（此刻已无事务/连接）。
3. `ingestAgentResult`、`createNote`、alignment update 各自短事务（Service 自带）即可；
   它们相互间无"要么全有要么全无"的业务要求（note 失败留下 source_document 可接受，
   与 knowledge_document.parse 失败路径的现有形态一致）。
4. 唯一需要补偿的窗口：**ingest 成功 → distill 抛异常**。包一层补偿，恢复原"仿佛没发生过"：

```java
SourceDocument source = ingestionService.ingestAgentResult(title, primaryUrl, truncated.rawText(), null, userId);
final String markdown;
try {
    markdown = truncated.rawText().length() > CHUNKED_DISTILL_THRESHOLD
            ? mergeDistillChunked(...)
            : mergeDistillChat(...);
} catch (Exception e) {
    try {
        sourceDocumentMapper.deleteById(source.getId());   // 补偿：删除孤儿 source_document
    } catch (Exception cleanupEx) {
        log.warn("补偿删除 source_document 失败 id={}", source.getId(), cleanupEx);
    }
    throw e;
}
```

5. `runSync`（`KnowledgeReadingJobServiceImpl.java:242`）与 worker 的 `executeDistill` 都经由
   `mergeDistill`，自动受益，无需改动。

### 两方案共同的回归清单

- [ ] `docker-compose.yml` 迁移预演环境跑一次完整精读（含 outlineConfirm=true 与 false 两分支）
- [ ] 提交时断开 DeepSeek/余额接口：submit 应快速失败且**不留** job 行（方案 A）；蒸馏中断断网：
      不留孤儿 note、source_document 被补偿删除（方案 B）
- [ ] 并发压测：5 个用户同时 submit，`/actuator/health` 与普通查询接口全程可用
      （重构前此场景可复现连接池耗尽）
- [ ] Hikari 池维持 5 不动（2GB 机器加大池是错药；本重构才是对症的）

---

## 不在本文档范围（后端其他待办，按审查报告优先级）

**运维/部署决策项，暂缓**：P0-1 HTTPS、P0-4 AI 端点限流/配额、P1-3 生产启用 Redis
（prod 现按 2G 机器显式 `app.redis.mode: off`，属已接受的取舍）。

**2026-09-12 已落地**：

- **P1-1 FULLTEXT 搜索** → 改走「搜索下推 SQL + 删除死索引」：笔记列表/搜索统一 JOIN
  `source_document` 下推到 SQL（`KnowledgeNoteMapper.selectSearchPage/countSearch`，keyword LIKE
  转义、显式 is_delete=0、INNER JOIN 复刻来源缺失跳过），不再全量拉内存过滤；博客搜索维持
  LIKE `%..%`（utf8mb4 *_ci 天然不区分大小写）。FULLTEXT 对中文需 ngram 分词且语义变为分词
  短语匹配（非子串包含），个人库量级性价比低，故在 `V19` 删除建表以来从未被查询使用的
  `blog_post.idx_search`（本机库验证幂等无操作跑通）。搜索 SQL 已在真库冒烟（计数/分页/中文关键词命中）。
- **P1-2 无限增长表保留策略** → `purgeExpired` 四表（ai_usage_log / ops_audit_log /
  http_access_log / biz_stat_daily）本就有，本次改为 `BatchDeletes.purge` 分批
  （`DELETE ... WHERE ... LIMIT 500` 循环 + 批间 50ms），积压清理不再单条大事务。
- **P1-6 安全三小项** → 全部闭合：`AiApplicationTests` 已 `@Tag("integration")` 默认跳过；
  `/actuator/health` `show-details: never`（本地排障在 application-local.yml 覆盖）；
  LLM 请求/响应日志 prod 全关（langchain4j 各工厂硬编码 false，仅 dev yml 显式开）。

完整清单见架构审查报告。
