package com.ai.model.vo.chat;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 聊天 SSE 流事件的统一信封：ask 与 agent 两条链路共用。
 *
 * <p>event 取值及含义：
 * <ul>
 *   <li>chunk —— 一段回复文本（agent 模式为最终文本一次性下发）</li>
 *   <li>segment_plan —— 分段计划：正文按 segments 拆分、按 delays 毫秒间隔模拟打字</li>
 *   <li>tool_call / tool_result —— agent 模式工具调用前后的过程事件（含步骤号）</li>
 *   <li>confirm_required —— agent 模式 L2 工具待用户确认：携带 confirmToken / summary /
 *       expiresInSec，前端展示确认卡片，用户确认或取消后走确认续跑（设计文档 §7.2 / §8.3）</li>
 *   <li>error / done —— 出错与正常收尾，前端收到 done 后关闭流</li>
 * </ul>
 * type 区分来源（agent / 普通），供前端区分渲染样式。
 */
@Data
public class ChatStreamEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EVENT_CHUNK = "chunk";
    public static final String EVENT_SEGMENT_PLAN = "segment_plan";
    public static final String EVENT_DONE = "done";
    public static final String EVENT_TOOL_CALL = "tool_call";
    public static final String EVENT_TOOL_RESULT = "tool_result";
    public static final String EVENT_CONFIRM_REQUIRED = "confirm_required";
    public static final String EVENT_ERROR = "error";

    private String event;

    private String d;

    private String type;

    private List<String> segments;

    private List<Long> delays;

    private String tool;

    private Map<String, Object> args;

    private Integer step;

    private Boolean success;

    private Map<String, Object> data;

    private AgentUiAction uiAction;

    private String message;

    /** confirm_required 专用：L2 待确认令牌，用户确认续跑时原样回传 */
    private String confirmToken;

    /** confirm_required 专用：待确认操作的人类可读摘要（如「将发布文章《xx》」） */
    private String summary;

    /** confirm_required 专用：确认令牌剩余有效期（秒），超时未确认需重新发起 */
    private Integer expiresInSec;

    public static ChatStreamEvent chunk(String text, String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_CHUNK);
        event.setD(text);
        event.setType(type);
        return event;
    }

    public static ChatStreamEvent segmentPlan(List<String> segments, List<Long> delays, String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_SEGMENT_PLAN);
        event.setSegments(segments);
        event.setDelays(delays);
        event.setType(type);
        return event;
    }

    public static ChatStreamEvent done(String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_DONE);
        event.setType(type);
        return event;
    }

    public static ChatStreamEvent toolCall(String tool, Map<String, Object> args, int step, String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_TOOL_CALL);
        event.setTool(tool);
        event.setArgs(args);
        event.setStep(step);
        event.setType(type);
        return event;
    }

    public static ChatStreamEvent toolResult(String tool, boolean success, Map<String, Object> data,
                                             AgentUiAction uiAction, int step, String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_TOOL_RESULT);
        event.setTool(tool);
        event.setSuccess(success);
        event.setData(data);
        event.setUiAction(uiAction);
        event.setStep(step);
        event.setType(type);
        return event;
    }

    public static ChatStreamEvent confirmRequired(String confirmToken, String tool, String summary,
                                                  int expiresInSec, String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_CONFIRM_REQUIRED);
        event.setConfirmToken(confirmToken);
        event.setTool(tool);
        event.setSummary(summary);
        event.setExpiresInSec(expiresInSec);
        event.setType(type);
        return event;
    }

    public static ChatStreamEvent error(String message, String type) {
        ChatStreamEvent event = new ChatStreamEvent();
        event.setEvent(EVENT_ERROR);
        event.setMessage(message);
        event.setType(type);
        return event;
    }
}
