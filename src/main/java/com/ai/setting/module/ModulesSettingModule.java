package com.ai.setting.module;

import com.ai.config.AppModuleProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import com.ai.setting.SettingValueCodec;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务模块开关设置模块：把 {@code app.modules.*} 的部署默认（yml / 环境变量）暴露为可运行期覆盖的 DB 设置。
 *
 * <p>刻意**不加** {@code @ConditionalOnModule}：模块开关自身必须无条件注册，
 * 否则某模块关闭后连重新打开它的入口都会消失。
 *
 * <p>schema 的 key 与 {@link com.ai.config.ModuleEnablementService#snapshot()} 完全一致（冻结契约）；
 * 字段默认值取自 {@link AppModuleProperties}，使「未覆盖」时设置页展示的就是真实生效的部署默认值
 * （prod 下 tts / app-lab 默认为关），而非硬编码 true。
 */
@Component
public class ModulesSettingModule implements SettingModule {

    /** key 顺序对齐 ModuleEnablementService.snapshot()；key 即 DB 覆盖行的 setting_key */
    private static final List<String> KEYS = List.of(
            "ops", "blog", "knowledge", "reading", "chat", "study", "diary", "worklog", "tts", "app-lab");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("ops", "运维中心"),
            Map.entry("blog", "博客"),
            Map.entry("knowledge", "知识库"),
            Map.entry("reading", "精读工作台"),
            Map.entry("chat", "对话"),
            Map.entry("study", "学习"),
            Map.entry("diary", "日记"),
            Map.entry("worklog", "工作手账"),
            Map.entry("tts", "语音"),
            Map.entry("app-lab", "应用工坊"));

    @Resource
    private AppModuleProperties appModuleProperties;

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_MODULES;
    }

    @Override
    public String displayName() {
        return "业务模块开关";
    }

    @Override
    public String phase() {
        return "P1";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        List<SettingFieldSchema> fields = new ArrayList<>(KEYS.size());
        for (String key : KEYS) {
            fields.add(SettingFieldSchema.builder()
                    .key(key)
                    .valueType(SiteSettingConstant.TYPE_BOOL)
                    .label(LABELS.get(key))
                    .description("关闭后前台入口与接口立即拒绝访问；彻底停用需重启应用")
                    // 未覆盖时展示部署默认（yml / APP_MODULES_*），避免与 /app/modules 不一致
                    .defaultValue(appModuleProperties.isEnabled(key))
                    .build());
        }
        return fields;
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
        for (Map.Entry<String, Object> entry : items.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "模块开关值不能为空: " + entry.getKey());
            }
            // toBool 对非法值抛 BusinessException，错误信息含原值
            SettingValueCodec.toBool(value);
        }
    }
}
