package com.ai.setting.module;

import com.ai.constant.SiteSettingConstant;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务模块开关。保存后立刻影响 GET /app/modules、菜单与路由门禁；
 * {@code @ConditionalOnModule} 的 Bean 装卸仍需重启。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class ModulesModule implements SettingModule {

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_MODULES;
    }

    @Override
    public String displayName() {
        return "业务模块";
    }

    @Override
    public String phase() {
        return "P0";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                flag("ops", "运维中心", "关闭后隐藏用量 / 审计 / 统计入口"),
                flag("blog", "随笔", "关闭后隐藏博客入口，相关接口在重启后不再注册"),
                flag("knowledge", "知识库", "关闭后隐藏知识库与精读工作台"),
                flag("reading", "精读任务", "关闭后停止精读异步任务队列"),
                flag("chat", "对话", "关闭后隐藏对话入口"),
                flag("study", "学习", "关闭后隐藏学习工作台"),
                flag("diary", "日记", "关闭后隐藏日记入口"),
                flag("worklog", "工作日志", "关闭后隐藏工作日志云端同步"),
                flag("tts", "语音合成", "关闭后隐藏 TTS 相关能力"),
                flag("app-lab", "实验室", "关闭后隐藏应用工坊 / 代码生成实验室")
        );
    }

    @Override
    public Map<String, Object> defaults() {
        Map<String, Object> map = new LinkedHashMap<>();
        for (SettingFieldSchema field : schema()) {
            map.put(field.getKey(), field.getDefaultValue());
        }
        return map;
    }

    @Override
    public void validate(Map<String, Object> items) {
        // boolean 字段由编解码校验
    }

    private SettingFieldSchema flag(String key, String label, String description) {
        return SettingFieldSchema.builder()
                .key(key)
                .valueType(SiteSettingConstant.TYPE_BOOL)
                .label(label)
                .description(description)
                .defaultValue(true)
                .danger(true)
                .sideEffect("关闭后前台入口立即隐藏；对应 Controller 的物理卸载需重启后端")
                .build();
    }
}
