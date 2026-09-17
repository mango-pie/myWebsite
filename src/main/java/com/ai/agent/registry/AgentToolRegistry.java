package com.ai.agent.registry;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 工具注册中心：启动时聚合容器里所有 {@link AgentToolModule}，
 * 逐个收集工具定义并按名称去重（重名直接启动失败，尽早暴露冲突），
 * 之后提供「按名查找」和「全量工具签名」两类查询，分别服务网关执行与模型调用。
 */
@Component
public class AgentToolRegistry {

    private final List<AgentToolModule> modules;

    @Getter
    private List<AgentToolDefinition> definitions = List.of();

    @Getter
    private List<String> moduleNames = List.of();

    private Map<String, AgentToolDefinition> definitionByName = Map.of();

    public AgentToolRegistry(List<AgentToolModule> modules) {
        this.modules = modules == null ? List.of() : modules;
    }

    @PostConstruct
    void init() {
        List<AgentToolDefinition> collected = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Map<String, AgentToolDefinition> byName = new LinkedHashMap<>();
        for (AgentToolModule module : modules) {
            names.add(module.moduleName());
            AgentToolRegistryBuilder builder = new AgentToolRegistryBuilder(module.moduleName());
            module.registerTools(builder);
            for (AgentToolDefinition definition : builder.buildDefinitions()) {
                if (byName.containsKey(definition.name())) {
                    throw new IllegalStateException("Duplicate agent tool name: " + definition.name());
                }
                byName.put(definition.name(), definition);
                collected.add(definition);
            }
        }
        this.definitions = List.copyOf(collected);
        this.moduleNames = List.copyOf(names);
        this.definitionByName = Collections.unmodifiableMap(byName);
    }

    public Optional<AgentToolDefinition> find(String name) {
        return Optional.ofNullable(definitionByName.get(name));
    }

    public List<dev.langchain4j.agent.tool.ToolSpecification> toolSpecifications() {
        return definitions.stream().map(AgentToolDefinition::specification).toList();
    }
}
