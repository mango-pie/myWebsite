package com.ai.setting.module;

import com.ai.config.knowledge.KnowledgeAiProperties;
import com.ai.config.knowledge.KnowledgeJinaProperties;
import com.ai.config.knowledge.KnowledgeRagProperties;
import com.ai.constant.SiteSettingConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.setting.SettingFieldSchema;
import com.ai.setting.SettingModule;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class KnowledgeModule implements SettingModule {

    private static final String REINDEX_SIDE_EFFECT =
            "变更后需对已有文档手动重建索引，不会自动重建";

    @Resource
    private KnowledgeAiProperties knowledgeAiProperties;
    @Resource
    private KnowledgeRagProperties knowledgeRagProperties;
    @Resource
    private KnowledgeJinaProperties knowledgeJinaProperties;

    @Override
    public String code() {
        return SiteSettingConstant.MODULE_KNOWLEDGE;
    }

    @Override
    public String displayName() {
        return "知识库 / RAG";
    }

    @Override
    public String phase() {
        return "P3";
    }

    @Override
    public List<SettingFieldSchema> schema() {
        return List.of(
                str("ai.chat_model", "聊天模型名", knowledgeAiProperties.getChatModel()),
                str("ai.embedding_model", "Embedding 模型名", knowledgeAiProperties.getEmbeddingModel()),
                dangerInt("ai.embedding_dimension", "向量维度",
                        nvl(knowledgeAiProperties.getEmbeddingDimension(), 1536), 256, 8192),
                dbl("ai.temperature", "聊天温度",
                        nvl(knowledgeAiProperties.getTemperature(), 0.2), 0, 2),
                intField("ai.max_tokens", "最大 tokens",
                        nvl(knowledgeAiProperties.getMaxTokens(), 2048), 64, 32768),
                intField("ai.timeout_seconds", "请求超时(秒)",
                        nvl(knowledgeAiProperties.getTimeoutSeconds(), 120), 10, 600),

                intField("rag.top_k", "检索 TopK", knowledgeRagProperties.getTopK(), 1, 50),
                dangerInt("rag.chunk_size", "切块大小",
                        knowledgeRagProperties.getChunkSize(), 200, 20000),
                dangerInt("rag.chunk_overlap", "切块重叠",
                        knowledgeRagProperties.getChunkOverlap(), 0, 5000),

                intField("jina.timeout_seconds", "Jina 超时(秒)",
                        knowledgeJinaProperties.getTimeoutSeconds(), 5, 300)
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
        if (items.containsKey("ai.chat_model") && blank(items.get("ai.chat_model"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ai.chat_model 不能为空");
        }
        if (items.containsKey("ai.embedding_model") && blank(items.get("ai.embedding_model"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "ai.embedding_model 不能为空");
        }
        if (items.containsKey("rag.chunk_size") && items.containsKey("rag.chunk_overlap")) {
            int size = toInt(items.get("rag.chunk_size"));
            int overlap = toInt(items.get("rag.chunk_overlap"));
            if (overlap >= size) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "rag.chunk_overlap 必须小于 rag.chunk_size");
            }
        }
    }

    @Override
    public void onChanged() {
        log.warn("knowledge 设置已变更：embedding_dimension / chunk 等危险参数不会自动重建索引，"
                + "请对已有文档手动重新处理");
    }

    private SettingFieldSchema dangerInt(String key, String label, int def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_INT).label(label).description(label)
                .defaultValue(def).min(min).max(max)
                .danger(true).sideEffect(REINDEX_SIDE_EFFECT)
                .build();
    }

    private SettingFieldSchema intField(String key, String label, int def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_INT).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }

    private SettingFieldSchema dbl(String key, String label, double def, double min, double max) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_DOUBLE).label(label).description(label)
                .defaultValue(def).min(min).max(max).build();
    }

    private SettingFieldSchema str(String key, String label, String def) {
        return SettingFieldSchema.builder()
                .key(key).valueType(SiteSettingConstant.TYPE_STRING).label(label).description(label)
                .defaultValue(def == null ? "" : def).build();
    }

    private static int nvl(Integer v, int def) {
        return v == null ? def : v;
    }

    private static double nvl(Double v, double def) {
        return v == null ? def : v;
    }

    private static boolean blank(Object v) {
        return v == null || String.valueOf(v).isBlank();
    }

    private static int toInt(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        return Integer.parseInt(String.valueOf(v).trim());
    }
}
