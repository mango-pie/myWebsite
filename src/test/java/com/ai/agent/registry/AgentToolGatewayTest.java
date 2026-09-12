package com.ai.agent.registry;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolLevel;
import com.ai.agent.model.AgentToolResult;
import com.ai.exception.BusinessException;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentToolGatewayTest {

    private AgentToolGateway gateway;
    private AgentToolRegistry registry;

    @BeforeEach
    void setUp() {
        gateway = new AgentToolGateway();
        registry = new AgentToolRegistry(List.of(new TestToolModule()));
        registry.init();

        ChatAgentProperties properties = new ChatAgentProperties();
        properties.setL2Enabled(false);

        ReflectionTestUtils.setField(gateway, "agentToolRegistry", registry);
        ReflectionTestUtils.setField(gateway, "chatAgentProperties", properties);
    }

    @Test
    void execute_knownL0Tool() {
        AgentToolContext ctx = AgentToolContext.builder().userId(1L).build();
        AgentToolResult result = gateway.execute("echo_tool", "{\"value\":\"hi\"}", ctx);
        assertTrue(result.isSuccess());
        assertEquals("hi", result.getData().get("value"));
    }

    @Test
    void execute_unknownTool() {
        AgentToolContext ctx = AgentToolContext.builder().userId(1L).build();
        try {
            gateway.execute("missing_tool", "{}", ctx);
        } catch (BusinessException e) {
            assertTrue(e.getMessage().contains("未知工具"));
        }
    }

    @Test
    void execute_l2RejectedWhenDisabled() {
        AgentToolContext ctx = AgentToolContext.builder().userId(1L).build();
        AgentToolResult result = gateway.execute("danger_tool", "{}", ctx);
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("尚未开放"));
    }

    static class TestToolModule extends AgentToolModule {
        @Override
        public String moduleName() {
            return "test";
        }

        @Override
        protected void registerTools(AgentToolRegistryBuilder registry) {
            registry.register(
                    "echo_tool",
                    "echo",
                    AgentToolLevel.L0,
                    JsonObjectSchema.builder().build(),
                    (ctx, args) -> AgentToolResult.ok(AgentToolRegistryBuilder.mapOf("value", "hi"))
            );
            registry.register(
                    "danger_tool",
                    "danger",
                    AgentToolLevel.L2,
                    JsonObjectSchema.builder().build(),
                    (ctx, args) -> AgentToolResult.ok(AgentToolRegistryBuilder.mapOf())
            );
        }
    }
}
