package com.ai.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

/**
 * Resolve {@code app.modules.*} from the Environment (CLI / yaml / env vars),
 * not from the {@link AppModuleProperties} bean — that bean may still hold Java
 * field defaults when conditions are evaluated, which wrongly keeps modules on
 * while {@code @ConditionalOnProperty(havingValue="false")} already sees false.
 */
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

        Environment env = context.getEnvironment();
        for (String module : modules) {
            String key = AppModuleProperties.normalizeKey(module);
            // YAML / CLI use kebab-case; appLab field binds as app-lab
            String propertyName = "app.modules." + key;
            Boolean enabled = env.getProperty(propertyName, Boolean.class);
            if (enabled == null && ("app".equals(key) || "applab".equals(key))) {
                enabled = env.getProperty("app.modules.app-lab", Boolean.class);
            }
            if (enabled == null) {
                enabled = matchIfMissing;
            }
            if (!enabled) {
                return ConditionOutcome.noMatch(ConditionMessage.forCondition(ConditionalOnModule.class)
                        .because("module disabled: " + module + " (" + propertyName + "=false)"));
            }
        }
        return ConditionOutcome.match(ConditionMessage.forCondition(ConditionalOnModule.class)
                .because("all modules enabled: " + String.join(", ", modules)));
    }
}
