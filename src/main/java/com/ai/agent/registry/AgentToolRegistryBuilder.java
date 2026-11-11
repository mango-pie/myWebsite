package com.ai.agent.registry;

import com.ai.agent.model.AgentToolLevel;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolResult;

public final class AgentToolRegistryBuilder {

    private final String module;
    private final List<AgentToolDefinition> definitions = new ArrayList<>();

    public AgentToolRegistryBuilder(String module) {
        this.module = module;
    }

    public AgentToolRegistryBuilder l0(String name, String description,
                                       BiFunction<AgentToolContext, String, AgentToolResult> executor) {
        return register(name, description, AgentToolLevel.L0, JsonObjectSchema.builder().build(), executor);
    }

    public AgentToolRegistryBuilder l1(String name, String description, JsonObjectSchema parameters,
                                       BiFunction<AgentToolContext, String, AgentToolResult> executor) {
        return register(name, description, AgentToolLevel.L1, parameters, executor);
    }

    public AgentToolRegistryBuilder l2(String name, String description, JsonObjectSchema parameters,
                                       BiFunction<AgentToolContext, String, AgentToolResult> executor) {
        return register(name, description, AgentToolLevel.L2, parameters, null, executor);
    }

    /**
     * 注册带确认摘要的 L2 工具：summarizer 在挂起待确认时生成人类可读的操作摘要
     * （confirm_required 事件的 summary 字段，设计文档 §8.3）。
     */
    public AgentToolRegistryBuilder l2(String name, String description, JsonObjectSchema parameters,
                                       BiFunction<AgentToolContext, String, String> summarizer,
                                       BiFunction<AgentToolContext, String, AgentToolResult> executor) {
        return register(name, description, AgentToolLevel.L2, parameters, summarizer, executor);
    }

    public AgentToolRegistryBuilder register(String name, String description, AgentToolLevel level,
                                             JsonObjectSchema parameters,
                                             BiFunction<AgentToolContext, String, AgentToolResult> executor) {
        return register(name, description, level, parameters, null, executor);
    }

    private AgentToolRegistryBuilder register(String name, String description, AgentToolLevel level,
                                              JsonObjectSchema parameters,
                                              BiFunction<AgentToolContext, String, String> summarizer,
                                              BiFunction<AgentToolContext, String, AgentToolResult> executor) {
        ToolSpecification spec = ToolSpecification.builder()
                .name(name)
                .description(description)
                .parameters(parameters)
                .build();
        definitions.add(new AgentToolDefinition(name, description, level, module, spec, executor, summarizer));
        return this;
    }

    public List<AgentToolDefinition> buildDefinitions() {
        return List.copyOf(definitions);
    }

    public static JsonObjectSchema.Builder objectSchema() {
        return JsonObjectSchema.builder();
    }

    public static JsonStringSchema stringProp(String description) {
        return JsonStringSchema.builder().description(description).build();
    }

    public static JsonBooleanSchema booleanProp(String description) {
        return JsonBooleanSchema.builder().description(description).build();
    }

    public static JsonIntegerSchema integerProp(String description) {
        return JsonIntegerSchema.builder().description(description).build();
    }

    public static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            map.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return map;
    }
}
