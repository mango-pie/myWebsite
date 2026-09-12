package com.ai.service.knowledge.support;

import com.ai.config.ConditionalOnModule;

import com.ai.mapper.knowledge.SourceDocumentMapper;
import com.ai.model.entity.knowledge.SourceDocument;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 去重前置：判断一批 URL 是否已在库（source_document 已存在），
 * 采集 / 搜索候选上直接标注「已在库 / 已读」，避免重复学习与重复烧钱。
 */
@ConditionalOnModule("knowledge")
@Component
public class SourceDuplicateChecker {

    @Resource
    private SourceDocumentMapper sourceDocumentMapper;

    /** 返回 url → 是否已在库 */
    public Map<String, Boolean> checkIngested(List<String> urls, Long userId) {
        Map<String, Boolean> result = new HashMap<>();
        if (urls == null || urls.isEmpty() || userId == null) {
            return result;
        }
        List<SourceDocument> owned = sourceDocumentMapper.selectListByQuery(
                QueryWrapper.create().eq("user_id", userId).eq("is_delete", 0));
        if (owned.isEmpty()) {
            for (String url : urls) {
                result.put(url, false);
            }
            return result;
        }
        for (String url : urls) {
            if (url == null || url.isBlank()) {
                result.put(url, false);
                continue;
            }
            String normalized = normalize(url);
            boolean found = owned.stream()
                    .anyMatch(s -> normalized.equals(normalize(s.getSourceUrl())));
            result.put(url, found);
        }
        return result;
    }

    private String normalize(String url) {
        if (url == null) {
            return "";
        }
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) {
            u = u.substring(0, hash);
        }
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.startsWith("http://")) {
            u = "https://" + u.substring("http://".length());
        }
        return u.toLowerCase();
    }
}
