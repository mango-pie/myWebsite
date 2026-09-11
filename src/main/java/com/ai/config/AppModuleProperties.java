package com.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务模块运行时开关，默认全部启用。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.modules")
public class AppModuleProperties {

    private boolean ops = true;
    private boolean blog = true;
    private boolean knowledge = true;
    private boolean reading = true;
    private boolean chat = true;
    private boolean study = true;
    private boolean diary = true;
    private boolean tts = true;
    private boolean appLab = true;

    public boolean isEnabled(String moduleName) {
        if (moduleName == null || moduleName.isBlank()) {
            return false;
        }
        return switch (normalizeKey(moduleName)) {
            case "ops" -> ops;
            case "blog" -> blog;
            case "knowledge" -> knowledge;
            case "reading" -> reading;
            case "chat" -> chat;
            case "study" -> study;
            case "diary" -> diary;
            case "tts" -> tts;
            case "app", "app-lab", "applab" -> appLab;
            default -> false;
        };
    }

    static String normalizeKey(String moduleName) {
        return moduleName.trim().toLowerCase().replace('_', '-');
    }
}
