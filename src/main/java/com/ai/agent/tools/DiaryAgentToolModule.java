package com.ai.agent.tools;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolResult;
import com.ai.model.vo.chat.AgentUiAction;
import com.ai.agent.registry.AgentToolModule;
import com.ai.agent.registry.AgentToolRegistryBuilder;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.vo.diary.DiaryEntryVO;
import com.ai.service.DiaryEntryService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.ai.agent.registry.AgentToolRegistryBuilder.*;

/**
 * 日记域 Agent 工具：按日期查询（L0）、保存/更新指定日期日记（L1，按日期幂等覆盖）
 * 与删除指定日记（L2，经确认流后执行）。
 * 写操作成功后附带 refresh UI 动作，通知前端刷新日记页。
 * 仅在 chat 与 diary 模块同时开启时注册。
 */
@ConditionalOnModule({"chat", "diary"})
@Component
public class DiaryAgentToolModule extends AgentToolModule {

    @Resource
    private DiaryEntryService diaryEntryService;

    @Override
    public String moduleName() {
        return "diary";
    }

    @Override
    protected void registerTools(AgentToolRegistryBuilder registry) {
        registry.l1("get_diary_by_date", "按日期查询日记，默认今天",
                objectSchema()
                        .addProperty("date", stringProp("日期 yyyy-MM-dd，可空为今天"))
                        .build(),
                this::getDiaryByDate);

        registry.l1("save_diary_entry", "保存或更新指定日期的日记",
                objectSchema()
                        .addProperty("date", stringProp("日期 yyyy-MM-dd，可空为今天"))
                        .addProperty("title", stringProp("标题"))
                        .addProperty("content", stringProp("正文"))
                        .addProperty("mood", stringProp("心情"))
                        .addProperty("weather", stringProp("天气"))
                        .addProperty("status", integerProp("0 草稿 1 完成"))
                        .build(),
                this::saveDiaryEntry);

        // L2 高风险删除：网关会挂起待确认，用户确认后原参数重放执行（设计文档 §8.3）
        registry.l2("delete_diary_entry", "删除指定日记（需用户确认后执行）",
                objectSchema()
                        .addProperty("id", integerProp("日记 id"))
                        .required("id")
                        .build(),
                this::summarizeDeleteEntry,
                this::deleteDiaryEntry);
    }

    private AgentToolResult getDiaryByDate(AgentToolContext ctx, String args) {
        LocalDate date = parseDate(parseArgs(args).getStr("date"));
        DiaryEntryVO entry = diaryEntryService.getByDate(date, ctx.getUserId());
        if (entry == null) {
            return AgentToolResult.ok(mapOf("date", date.toString(), "exists", false));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exists", true);
        data.put("id", entry.getId());
        data.put("date", entry.getDiaryDate());
        data.put("title", entry.getTitle());
        data.put("content", entry.getContent());
        data.put("mood", entry.getMood());
        data.put("weather", entry.getWeather());
        return AgentToolResult.ok(data);
    }

    private AgentToolResult saveDiaryEntry(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        LocalDate date = parseDate(json.getStr("date"));
        DiaryEntrySaveRequest request = new DiaryEntrySaveRequest();
        request.setDiaryDate(date);
        request.setTitle(json.getStr("title"));
        request.setContent(json.getStr("content"));
        request.setMood(json.getStr("mood"));
        request.setWeather(json.getStr("weather"));
        if (json.containsKey("status")) {
            request.setStatus(json.getInt("status"));
        }
        long id = diaryEntryService.saveDiaryEntry(request, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("refresh")
                .module("diary_day")
                .build();
        return AgentToolResult.ok(mapOf("id", id, "date", date.toString()), uiAction);
    }

    private AgentToolResult deleteDiaryEntry(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        Long id = json.getLong("id");
        if (id == null || id <= 0) {
            return AgentToolResult.fail("id 不能为空");
        }
        // deleteDiaryEntry 内部校验日记归属，非本人/不存在时抛业务异常，由网关收敛为 fail
        diaryEntryService.deleteDiaryEntry(id, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("refresh")
                .module("diary_day")
                .build();
        return AgentToolResult.ok(mapOf("diaryId", id, "deleted", true), uiAction);
    }

    /** 确认摘要：日记服务无按 id 查询，固定回退为日记 id 展示。 */
    private String summarizeDeleteEntry(AgentToolContext ctx, String args) {
        Long id = parseArgs(args).getLong("id");
        return "删除日记（id=" + id + "）";
    }

    private LocalDate parseDate(String raw) {
        if (StrUtil.isBlank(raw)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new com.ai.exception.BusinessException(
                    com.ai.exception.ErrorCode.PARAMS_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    private JSONObject parseArgs(String args) {
        if (StrUtil.isBlank(args)) {
            return new JSONObject();
        }
        return JSONUtil.parseObj(args);
    }
}
