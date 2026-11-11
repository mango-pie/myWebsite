package com.ai.agent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatAgentPropertiesTest {

    @Test
    void bindsL2ConfirmPropertiesFromYaml() {
        Map<String, String> source = Map.of(
                "chat.agent.l2-enabled", "false",
                "chat.agent.l2-confirm-ttl-seconds", "120",
                "chat.agent.l2-confirm-max-pending", "50");
        ChatAgentProperties properties = new ChatAgentProperties();

        new Binder(new MapConfigurationPropertySource(source))
                .bind("chat.agent", Bindable.ofInstance(properties));

        assertFalse(properties.isL2Enabled());
        assertEquals(120, properties.getL2ConfirmTtlSeconds());
        assertEquals(50, properties.getL2ConfirmMaxPending());
    }

    @Test
    void l2DefaultsStayOffWithFiveMinuteTtl() {
        ChatAgentProperties properties = new ChatAgentProperties();

        assertFalse(properties.isL2Enabled());
        assertEquals(300, properties.getL2ConfirmTtlSeconds());
        assertEquals(1000, properties.getL2ConfirmMaxPending());
    }
}
