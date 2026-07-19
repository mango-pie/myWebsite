package com.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

class OnModuleCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnModule.class.getName());
        if (attrs == null) {
            return ConditionOutcome.noMatch("@ConditionalOnModule not present");
        }
        String[] modules = (String[]) attrs.get("value");
        boolean matchIfMissing = (boolean) attrs.get("matchIfMissing");
        if (modules == null || modules.length == 0) {
            return ConditionOutcome.noMatch("No module names specified on @ConditionalOnModule");
        }

        AppModuleProperties properties = context.getBeanFactory()
                .getBeanProvider(AppModuleProperties.class)
                .getIfAvailable();
        if (properties == null) {
            return matchIfMissing
                    ? ConditionOutcome.match("AppModuleProperties missing, matchIfMissing=true")
                    : ConditionOutcome.noMatch("AppModuleProperties missing, matchIfMissing=false");
        }

        for (String module : modules) {
            if (!properties.isEnabled(module)) {
                return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnModule.class)
                        .because("module disabled: " + module));
            }
        }
        return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnModule.class)
                .because("all modules enabled: " + String.join(", ", modules)));
    }
}
