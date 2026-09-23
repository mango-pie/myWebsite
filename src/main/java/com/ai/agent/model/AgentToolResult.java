package com.ai.agent.model;

import cn.hutool.json.JSONUtil;
import com.ai.model.vo.chat.AgentUiAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolResult {

    private boolean success;

    private Map<String, Object> data;

    private String error;

    private AgentUiAction uiAction;

    public String toJsonForLlm() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", success);
        if (data != null && !data.isEmpty()) {
            payload.put("data", data);
        }
        if (error != null) {
            payload.put("error", error);
        }
        return JSONUtil.toJsonStr(payload);
    }

    public static AgentToolResult ok(Map<String, Object> data) {
        return AgentToolResult.builder().success(true).data(data).build();
    }

    public static AgentToolResult ok(Map<String, Object> data, AgentUiAction uiAction) {
        return AgentToolResult.builder().success(true).data(data).uiAction(uiAction).build();
    }

    public static AgentToolResult fail(String error) {
        return AgentToolResult.builder().success(false).error(error).build();
    }
}
