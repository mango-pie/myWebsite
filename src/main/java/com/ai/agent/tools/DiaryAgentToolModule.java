package com.ai.agent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolResult;
import com.ai.agent.model.AgentUiAction;
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
