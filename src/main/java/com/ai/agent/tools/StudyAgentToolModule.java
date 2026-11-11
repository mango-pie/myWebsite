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
import com.ai.constant.StudyConstant;
import com.ai.model.dto.study.StudyTaskAddRequest;
import com.ai.model.dto.study.StudyTaskToggleRequest;
import com.ai.model.dto.study.StudyTaskViewQueryRequest;
import com.ai.model.entity.StudyList;
import com.ai.model.vo.study.StudyListVO;
import com.ai.model.vo.study.StudyTaskVO;
import com.ai.service.StudyListService;
import com.ai.service.StudyTaskService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ai.agent.registry.AgentToolRegistryBuilder.*;

/**
 * 学习域 Agent 工具：清单/今日任务查询，创建任务（可加入今日待办，缺省清单为收集箱）、
 * 切换完成状态与删除任务（L2，经确认流后执行）。写操作成功后附带 refresh UI 动作刷新待办页。
 * 仅在 chat 与 study 模块同时开启时注册。
 */
@ConditionalOnModule({"chat", "study"})
@Component
public class StudyAgentToolModule extends AgentToolModule {

    @Resource
    private StudyListService studyListService;

    @Resource
    private StudyTaskService studyTaskService;

    @Override
    public String moduleName() {
        return "study";
    }

    @Override
    protected void registerTools(AgentToolRegistryBuilder registry) {
        registry.l0("list_study_lists", "列出当前用户所有学习清单（含收集箱）", this::listStudyLists);

        registry.l1("list_today_tasks", "列出今日待办任务", objectSchema().build(), this::listTodayTasks);

        registry.l1("create_study_task", "创建学习任务，可加入今日待办",
                objectSchema()
                        .addProperty("title", stringProp("任务标题"))
                        .addProperty("isToday", booleanProp("是否加入今日待办"))
                        .addProperty("listId", integerProp("所属清单 id，可空则使用收集箱"))
                        .addProperty("content", stringProp("任务详情"))
                        .addProperty("dueDate", stringProp("截止日期 yyyy-MM-dd"))
                        .addProperty("priority", integerProp("优先级 1-4"))
                        .required("title")
                        .build(),
                this::createStudyTask);

        registry.l1("toggle_study_task", "切换任务完成状态",
                objectSchema()
                        .addProperty("id", integerProp("任务 id"))
                        .addProperty("done", booleanProp("是否完成，可空则切换"))
                        .required("id")
                        .build(),
                this::toggleStudyTask);

        // L2 高风险删除：网关会挂起待确认，用户确认后原参数重放执行（设计文档 §8.3）
        registry.l2("delete_study_task", "删除指定学习任务（需用户确认后执行）",
                objectSchema()
                        .addProperty("id", integerProp("任务 id"))
                        .required("id")
                        .build(),
                this::summarizeDeleteTask,
                this::deleteStudyTask);
    }

    private AgentToolResult listStudyLists(AgentToolContext ctx, String args) {
        List<StudyListVO> lists = studyListService.getAllLists(ctx.getUserId());
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (StudyListVO list : lists) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", list.getId());
            item.put("name", list.getName());
            item.put("listType", list.getListType());
            item.put("taskCount", list.getTaskCount());
            simplified.add(item);
        }
        return AgentToolResult.ok(mapOf("lists", simplified));
    }

    private AgentToolResult listTodayTasks(AgentToolContext ctx, String args) {
        StudyTaskViewQueryRequest request = new StudyTaskViewQueryRequest();
        request.setView(StudyConstant.StudyView.TODAY.getValue());
        request.setPageNum(1);
        request.setPageSize(50);
        request.setHideCompleted(false);
        Page<StudyTaskVO> page = studyTaskService.queryTaskView(request, ctx.getUserId());
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (StudyTaskVO task : page.getRecords()) {
            simplified.add(simplifyTask(task));
        }
        return AgentToolResult.ok(mapOf("tasks", simplified, "total", page.getTotalRow()));
    }

    private AgentToolResult createStudyTask(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        String title = json.getStr("title");
        if (StrUtil.isBlank(title)) {
            return AgentToolResult.fail("title 不能为空");
        }

        StudyTaskAddRequest request = new StudyTaskAddRequest();
        request.setTitle(title.trim());
        request.setContent(json.getStr("content"));
        request.setDueDate(json.getStr("dueDate"));
        if (json.containsKey("priority")) {
            request.setPriority(json.getInt("priority"));
        }
        if (json.containsKey("isToday")) {
            request.setIsToday(json.getBool("isToday"));
        }

        Long listId = json.getLong("listId");
        if (listId == null || listId <= 0) {
            StudyList inbox = studyListService.getOrCreateInbox(ctx.getUserId());
            listId = inbox.getId();
        }
        request.setListId(listId);
        request.setSourceType(StudyConstant.SOURCE_TYPE_MANUAL);

        long taskId = studyTaskService.addTask(request, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("refresh")
                .module("study_today")
                .build();
        return AgentToolResult.ok(mapOf("taskId", taskId, "title", title.trim(), "listId", listId), uiAction);
    }

    private AgentToolResult toggleStudyTask(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        Long id = json.getLong("id");
        if (id == null || id <= 0) {
            return AgentToolResult.fail("id 不能为空");
        }
        StudyTaskToggleRequest request = new StudyTaskToggleRequest();
        request.setId(id);
        if (json.containsKey("done")) {
            request.setDone(json.getBool("done"));
        }
        studyTaskService.toggleTask(request, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("refresh")
                .module("study_today")
                .build();
        return AgentToolResult.ok(mapOf("taskId", id), uiAction);
    }

    private AgentToolResult deleteStudyTask(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        Long id = json.getLong("id");
        if (id == null || id <= 0) {
            return AgentToolResult.fail("id 不能为空");
        }
        // deleteTask 内部校验任务归属，非本人/不存在时抛业务异常，由网关收敛为 fail
        studyTaskService.deleteTask(id, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("refresh")
                .module("study_today")
                .build();
        return AgentToolResult.ok(mapOf("taskId", id, "deleted", true), uiAction);
    }

    /** 确认摘要：待删除任务标题（查不到时回退为任务 id）。 */
    private String summarizeDeleteTask(AgentToolContext ctx, String args) {
        Long id = parseArgs(args).getLong("id");
        String title = null;
        try {
            StudyTaskVO task = id == null ? null : studyTaskService.getTaskVO(id, ctx.getUserId());
            title = task == null ? null : task.getTitle();
        } catch (Exception ignore) {
            // 摘要查询失败不阻断确认流，回退为 id 展示
        }
        return title == null ? "删除学习任务（id=" + id + "）" : "删除学习任务「" + title + "」";
    }

    private Map<String, Object> simplifyTask(StudyTaskVO task) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", task.getId());
        item.put("title", task.getTitle());
        item.put("status", task.getStatus());
        item.put("done", task.getStatus() != null && task.getStatus() == StudyConstant.TASK_STATUS_DONE);
        item.put("isToday", task.getIsToday());
        item.put("listId", task.getListId());
        item.put("dueDate", task.getDueDate());
        return item;
    }

    private JSONObject parseArgs(String args) {
        if (StrUtil.isBlank(args)) {
            return new JSONObject();
        }
        return JSONUtil.parseObj(args);
    }
}
