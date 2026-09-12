package com.ai.interceptor;

import com.ai.config.ModuleEnablementService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 业务模块 API 运行时门禁：模块关闭后对应路径立即返回 40400，
 * 不必等重启后 {@code @ConditionalOnModule} 的 Bean 卸载。
 *
 * <p>前缀映射与前端 {@code AI-frontend/src/utils/moduleGate.ts} 的 PATH_REQUIRE_MODULE 对齐，
 * 新增业务前缀需两侧同步。/app/modules 是模块能力查询本身，永远放行。
 *
 * <p>进程内 5 秒覆盖快照：Redis 关闭时 SiteSettingCache 每次都直查 DB，
 * 门禁挂在全站路径上不能每请求查库；代价是开关生效最多延迟 5 秒
 * （设置保存后的即时性由前端主动重拉 /app/modules 保证，不受影响）。
 */
@Component
public class ModuleGateInterceptor implements HandlerInterceptor {

    private static final long SNAPSHOT_TTL_NANOS = 5_000_000_000L;

    private static final String MODULE_DISABLED_BODY =
            "{\"code\":40400,\"data\":null,\"message\":\"该功能未启用\"}";

    /** 插入序即匹配序，长前缀必须在前（/admin/knowledge 先于 /knowledge） */
    private static final Map<String, String> PREFIX_MODULE = new LinkedHashMap<>();

    static {
        PREFIX_MODULE.put("/admin/knowledge", "knowledge");
        PREFIX_MODULE.put("/admin/ops", "ops");
        PREFIX_MODULE.put("/chatHistory", "chat");
        PREFIX_MODULE.put("/chat", "chat");
        PREFIX_MODULE.put("/diary", "diary");
        PREFIX_MODULE.put("/kb", "knowledge");
        PREFIX_MODULE.put("/knowledge", "knowledge");
        PREFIX_MODULE.put("/blog", "blog");
        PREFIX_MODULE.put("/study", "study");
        PREFIX_MODULE.put("/worklog", "worklog");
        PREFIX_MODULE.put("/tts", "tts");
        PREFIX_MODULE.put("/app", "app-lab");
    }

    @Resource
    private ModuleEnablementService moduleEnablementService;

    private volatile Map<String, Boolean> snapshot = Map.of();
    private volatile long snapshotAt = 0L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = pathWithinApplication(request);
        if (path.equals("/app/modules")) {
            return true;
        }
        String module = null;
        for (Map.Entry<String, String> entry : PREFIX_MODULE.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                module = entry.getValue();
                break;
            }
        }
        if (module == null || isEnabled(module)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MODULE_DISABLED_BODY);
        return false;
    }

    private boolean isEnabled(String module) {
        Map<String, Boolean> snap = this.snapshot;
        long now = System.nanoTime();
        if (snap.isEmpty() || now - snapshotAt > SNAPSHOT_TTL_NANOS) {
            snap = Map.copyOf(moduleEnablementService.snapshot());
            this.snapshot = snap;
            this.snapshotAt = now;
        }
        Boolean enabled = snap.get(module);
        return enabled == null || enabled;
    }

    /** getRequestURI 含 context-path（/api），剥离后与 Controller 映射同基准 */
    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
