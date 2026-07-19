package com.ai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.SiteSettingAuditConstant;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.platform.SiteSettingMapper;
import com.ai.model.dto.setting.SiteSettingUpdateRequest;
import com.ai.model.entity.SiteSetting;
import com.ai.model.vo.setting.SettingFieldMetaVO;
import com.ai.model.vo.setting.SettingFieldSchemaVO;
import com.ai.model.vo.setting.SettingModuleSchemaVO;
import com.ai.model.vo.setting.SettingModuleVO;
import com.ai.model.vo.setting.SettingModuleValuesVO;
import com.ai.model.vo.setting.SiteSettingAuditVO;
import com.ai.model.vo.setting.SiteSettingsBootstrapVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.SiteSettingAuditService;
import com.ai.service.SiteSettingService;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import com.ai.setting.SettingModuleRegistry;
import com.ai.setting.SettingValueCodec;
import com.ai.setting.SiteSettingCache;
import com.ai.setting.SiteSettingCrypto;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SiteSettingServiceImpl implements SiteSettingService {

    @Resource
    private SiteSettingMapper siteSettingMapper;

    @Resource
    private SettingModuleRegistry registry;

    @Resource
    private SiteSettingCache siteSettingCache;

    @Resource
    private SiteSettingCrypto siteSettingCrypto;

    @Resource
    private SiteSettingAuditService siteSettingAuditService;

    /**
     * Lazy: OpsAuditLogService → OpsRuntimeSettings → SiteSettingService，
     * 若这里急切注入会形成启动期循环依赖。
     */
    @Lazy
    @Resource
    private OpsAuditLogService opsAuditLogService;

    @Override
    public List<SettingModuleVO> listModules() {
        List<SettingModuleVO> list = new ArrayList<>();
        for (SettingModule module : registry.all()) {
            list.add(toModuleVO(module));
        }
        return list;
    }

    @Override
    public SettingModuleSchemaVO getSchema(String moduleCode) {
        SettingModule module = registry.require(moduleCode);
        List<SettingFieldSchemaVO> fields = new ArrayList<>();
        for (SettingFieldSchema field : module.schema()) {
            fields.add(SettingFieldSchemaVO.builder()
                    .key(field.getKey())
                    .valueType(field.getValueType())
                    .label(field.getLabel())
                    .description(field.getDescription())
                    .defaultValue(field.getDefaultValue())
                    .sensitive(field.isSensitive())
                    .min(field.getMin())
                    .max(field.getMax())
                    .enumValues(field.getEnumValues())
                    .danger(field.isDanger())
                    .build());
        }
        return SettingModuleSchemaVO.builder()
                .module(module.code())
                .displayName(module.displayName())
                .fields(fields)
                .build();
    }

    @Override
    public SettingModuleValuesVO getModuleValues(String moduleCode) {
        SettingModule module = registry.require(moduleCode);
        Map<String, String> dbRaw = loadDbRaw(moduleCode);
        Map<String, Object> values = new LinkedHashMap<>();
        Map<String, SettingFieldMetaVO> meta = new LinkedHashMap<>();

        for (SettingFieldSchema field : module.schema()) {
            String key = field.getKey();
            boolean fromDb = dbRaw.containsKey(key);
            Object value;
            if (fromDb) {
                value = decodeStored(dbRaw.get(key), field);
            } else {
                value = field.getDefaultValue();
            }
            boolean sensitive = field.isSensitive();
            boolean configured = fromDb && StrUtil.isNotBlank(dbRaw.get(key));
            if (sensitive) {
                String plainForHint = configured ? String.valueOf(value) : null;
                values.put(key, maskSensitive(configured, plainForHint));
            } else {
                values.put(key, value);
            }
            meta.put(key, SettingFieldMetaVO.builder()
                    .source(fromDb ? SiteSettingConstant.SOURCE_DB : SiteSettingConstant.SOURCE_DEFAULT)
                    .sensitive(sensitive)
                    .configured(configured)
                    .build());
        }
        return SettingModuleValuesVO.builder()
                .module(module.code())
                .values(values)
                .meta(meta)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModule(String moduleCode, SiteSettingUpdateRequest request, Long operatorId) {
        SettingModule module = registry.require(moduleCode);
        if (!module.writable()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模块不可写: " + moduleCode);
        }
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "items 不能为空");
        }

        Map<String, SettingFieldSchema> schemaMap = module.schemaByKey();
        Map<String, Object> toValidate = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : request.getItems().entrySet()) {
            String key = entry.getKey();
            SettingFieldSchema field = schemaMap.get(key);
            if (field == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知配置项: " + key);
            }
            Object value = entry.getValue();
            if (field.isSensitive() && isKeepUnchanged(value)) {
                continue;
            }
            toValidate.put(key, value);
        }
        if (!toValidate.isEmpty()) {
            module.validate(toValidate);
        }

        Map<String, String> dbRawBefore = loadDbRawFresh(moduleCode);
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, Object> entry : toValidate.entrySet()) {
            String key = entry.getKey();
            SettingFieldSchema field = schemaMap.get(key);
            String oldDisplay = auditDisplayOld(dbRawBefore.get(key), field);
            String encoded = SettingValueCodec.encode(entry.getValue(), field.getValueType());
            String newDisplay = field.isSensitive()
                    ? SiteSettingAuditConstant.MASKED
                    : abbreviate(encoded);
            if (field.isSensitive() && StrUtil.isNotBlank(encoded)) {
                encoded = siteSettingCrypto.encrypt(encoded);
            }
            upsert(moduleCode, key, encoded, field.getValueType(), field.isSensitive(), operatorId, now);
            siteSettingAuditService.record(moduleCode, key, oldDisplay, newDisplay,
                    operatorId, SiteSettingAuditConstant.ACTION_UPDATE);
            if (SiteSettingConstant.MODULE_SITE.equals(moduleCode) && "maintenance_mode".equals(key)) {
                opsAuditLogService.audit(
                        OpsAuditActionConstant.SITE_MAINTENANCE_TOGGLE,
                        operatorId,
                        OpsAuditActionConstant.RESOURCE_SITE,
                        "maintenance_mode",
                        true,
                        Map.of("oldValue", oldDisplay == null ? "" : oldDisplay,
                                "newValue", newDisplay == null ? "" : newDisplay),
                        null);
            }
        }

        siteSettingCache.evict(moduleCode);
        module.onChanged();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetModule(String moduleCode, Long operatorId) {
        SettingModule module = registry.require(moduleCode);
        Map<String, SettingFieldSchema> schemaMap = module.schemaByKey();
        QueryWrapper query = QueryWrapper.create()
                .eq(SiteSetting::getModule, moduleCode);
        List<SiteSetting> existing = siteSettingMapper.selectListByQuery(query);
        for (SiteSetting row : existing) {
            SettingFieldSchema field = schemaMap.get(row.getSettingKey());
            String oldDisplay = field != null
                    ? auditDisplayOld(row.getSettingValue(), field)
                    : SiteSettingAuditConstant.MASKED;
            siteSettingAuditService.record(moduleCode, row.getSettingKey(), oldDisplay, "default",
                    operatorId, SiteSettingAuditConstant.ACTION_RESET);
        }
        siteSettingMapper.deleteByQuery(query);
        siteSettingCache.evict(moduleCode);
        module.onChanged();
    }

    @Override
    public Page<SiteSettingAuditVO> pageAudit(String module, int pageNum, int pageSize) {
        return siteSettingAuditService.page(module, pageNum, pageSize);
    }

    @Override
    public SiteSettingsBootstrapVO bootstrap(boolean admin) {
        return SiteSettingsBootstrapVO.builder()
                .admin(admin)
                .siteName(getString(SiteSettingConstant.MODULE_SITE, "name", "Ai Scene"))
                .siteSlogan(getString(SiteSettingConstant.MODULE_SITE, "slogan", ""))
                .modules(listModules())
                .build();
    }

    @Override
    public String getString(String module, String key, String defaultValue) {
        Object value = resolveValue(module, key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    @Override
    public boolean getBool(String module, String key, boolean defaultValue) {
        Object value = resolveValue(module, key);
        if (value == null) {
            return defaultValue;
        }
        return SettingValueCodec.toBool(value);
    }

    @Override
    public int getInt(String module, String key, int defaultValue) {
        Object value = resolveValue(module, key);
        if (value == null) {
            return defaultValue;
        }
        return (int) SettingValueCodec.toLong(value);
    }

    @Override
    public double getDouble(String module, String key, double defaultValue) {
        Object value = resolveValue(module, key);
        if (value == null) {
            return defaultValue;
        }
        return SettingValueCodec.toDouble(value);
    }

    @Override
    public Optional<String> findOverride(String module, String key) {
        SettingModule settingModule = registry.require(module);
        SettingFieldSchema field = settingModule.schemaByKey().get(key);
        if (field == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知配置项: " + module + "." + key);
        }
        Map<String, String> dbRaw = loadDbRaw(module);
        if (!dbRaw.containsKey(key) || StrUtil.isBlank(dbRaw.get(key))) {
            return Optional.empty();
        }
        Object decoded = decodeStored(dbRaw.get(key), field);
        if (decoded == null) {
            return Optional.empty();
        }
        String text = String.valueOf(decoded);
        return StrUtil.isBlank(text) ? Optional.empty() : Optional.of(text);
    }

    private Object resolveValue(String moduleCode, String key) {
        SettingModule module = registry.require(moduleCode);
        SettingFieldSchema field = module.schemaByKey().get(key);
        if (field == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "未知配置项: " + moduleCode + "." + key);
        }
        Map<String, String> dbRaw = loadDbRaw(moduleCode);
        if (dbRaw.containsKey(key)) {
            return decodeStored(dbRaw.get(key), field);
        }
        Object def = field.getDefaultValue();
        return def != null ? def : module.defaults().get(key);
    }

    private Object decodeStored(String stored, SettingFieldSchema field) {
        String raw = stored;
        if (field.isSensitive()) {
            raw = siteSettingCrypto.decrypt(stored);
        }
        return SettingValueCodec.decode(raw, field.getValueType());
    }

    private Map<String, String> loadDbRaw(String moduleCode) {
        return siteSettingCache.getModuleRaw(moduleCode, () -> loadDbRawFresh(moduleCode));
    }

    private Map<String, String> loadDbRawFresh(String moduleCode) {
        QueryWrapper query = QueryWrapper.create()
                .eq(SiteSetting::getModule, moduleCode);
        List<SiteSetting> rows = siteSettingMapper.selectListByQuery(query);
        Map<String, String> map = new HashMap<>();
        for (SiteSetting row : rows) {
            map.put(row.getSettingKey(), row.getSettingValue());
        }
        return map;
    }

    private String auditDisplayOld(String stored, SettingFieldSchema field) {
        if (StrUtil.isBlank(stored)) {
            return "";
        }
        if (field != null && field.isSensitive()) {
            return SiteSettingAuditConstant.MASKED;
        }
        if (field == null) {
            return SiteSettingAuditConstant.MASKED;
        }
        try {
            Object decoded = SettingValueCodec.decode(stored, field.getValueType());
            return abbreviate(decoded == null ? "" : String.valueOf(decoded));
        } catch (Exception e) {
            return SiteSettingAuditConstant.MASKED;
        }
    }

    private String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= 500) {
            return text;
        }
        return text.substring(0, 500) + "...";
    }

    private void upsert(String module, String key, String value, String valueType,
                        boolean sensitive, Long operatorId, LocalDateTime now) {
        QueryWrapper query = QueryWrapper.create()
                .eq(SiteSetting::getModule, module)
                .eq(SiteSetting::getSettingKey, key);
        SiteSetting existing = siteSettingMapper.selectOneByQuery(query);
        if (existing == null) {
            SiteSetting row = SiteSetting.builder()
                    .module(module)
                    .settingKey(key)
                    .settingValue(value)
                    .valueType(valueType)
                    .sensitive(sensitive ? 1 : 0)
                    .updatedBy(operatorId)
                    .createTime(now)
                    .updateTime(now)
                    .isDelete(0)
                    .build();
            siteSettingMapper.insert(row);
        } else {
            existing.setSettingValue(value);
            existing.setValueType(valueType);
            existing.setSensitive(sensitive ? 1 : 0);
            existing.setUpdatedBy(operatorId);
            existing.setUpdateTime(now);
            siteSettingMapper.update(existing);
        }
    }

    private boolean isKeepUnchanged(Object value) {
        return value == null || (value instanceof String s && s.isBlank());
    }

    private Object maskSensitive(boolean configured, String raw) {
        Map<String, Object> mask = new LinkedHashMap<>();
        mask.put("configured", configured);
        if (configured && StrUtil.isNotBlank(raw) && raw.length() >= 4) {
            mask.put("hint", "****" + raw.substring(raw.length() - 4));
        } else {
            mask.put("hint", configured ? "****" : "");
        }
        return mask;
    }

    private SettingModuleVO toModuleVO(SettingModule module) {
        return SettingModuleVO.builder()
                .code(module.code())
                .displayName(module.displayName())
                .phase(module.phase())
                .writable(module.writable())
                .build();
    }
}
