package com.ai.service.knowledge.learning;

import com.ai.constant.knowledge.LearningTreeIntentConstant;
import com.ai.constant.knowledge.LearningTreeRedisConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LearningGatePassStore {

    private static final long TTL_SECONDS = LearningTreeRedisConstant.GATE_PASS_TTL_SECONDS;
    private static final long JUDGMENT_TTL_SECONDS = LearningTreeRedisConstant.GATE_JUDGMENT_TTL_SECONDS;
    private static final int MEMORY_MAX_ENTRIES = 64;

    private final Map<String, LearningGatePass> memoryStore = boundedMemoryMap();
    private final Map<String, Judgment> memoryJudgments = boundedMemoryMap();

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.redis.available:false}")
    private boolean redisAvailable;

    private boolean useRedis() {
        return redisAvailable && stringRedisTemplate != null;
    }

    public String issue(LearningGatePass pass) {
        String id = UUID.randomUUID().toString().replace("-", "");
        pass.setGatePassId(id);
        pass.setExpiresAt(Instant.now().plusSeconds(TTL_SECONDS));
        if (useRedis()) {
            try {
                String json = objectMapper.writeValueAsString(pass);
                stringRedisTemplate.opsForValue().set(
                        LearningTreeRedisConstant.gatePassKey(id),
                        json,
                        TTL_SECONDS,
                        TimeUnit.SECONDS);
                return id;
            } catch (JsonProcessingException e) {
                log.warn("Redis gatePass serialize failed, fallback memory: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("Redis gatePass issue failed, fallback memory: {}", e.getMessage());
            }
        }
        memoryStore.put(id, pass);
        return id;
    }

    public LearningGatePass get(String gatePassId) {
        if (gatePassId == null || gatePassId.isBlank()) {
            return null;
        }
        String id = gatePassId.trim();
        if (useRedis()) {
            try {
                String json = stringRedisTemplate.opsForValue().get(LearningTreeRedisConstant.gatePassKey(id));
                if (json == null || json.isBlank()) {
                    return null;
                }
                return objectMapper.readValue(json, LearningGatePass.class);
            } catch (Exception e) {
                log.debug("Redis gatePass get failed, try memory: {}", e.getMessage());
            }
        }
        LearningGatePass pass = memoryStore.get(id);
        if (pass == null) {
            return null;
        }
        if (pass.getExpiresAt() != null && Instant.now().isAfter(pass.getExpiresAt())) {
            memoryStore.remove(id);
            return null;
        }
        return pass;
    }

    public LearningGatePass requireValid(Long userId, Long domainId, String gatePassId) {
        LearningGatePass pass = get(gatePassId);
        if (pass == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "门闩已失效，请重新判定");
        }
        if (!pass.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "门闩与用户不匹配");
        }
        if (domainId != null && !pass.getDomainId().equals(domainId)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "门闩与当前领域不匹配");
        }
        return pass;
    }

    public void validateForV2(Long userId, Long domainId, String gatePassId, boolean requireLeaf) {
        LearningGatePass pass = requireValid(userId, domainId, gatePassId);
        if (!pass.isRelated()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前问题与领域不相关，无法搜文");
        }
        if (pass.isOverridden()) {
            // 人工 override 的 pass 不受 LEAF 强校验限制（评审 §3.1）
            return;
        }
        if (requireLeaf && !LearningTreeIntentConstant.LEAF.equals(pass.getIntent())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "当前意图不是文章级精读，无法搜文合蒸");
        }
    }

    // ---------------- 判定结果缓存（domainId + question） ----------------

    public Judgment getCachedJudgment(Long domainId, String goal) {
        if (domainId == null || goal == null || goal.isBlank()) {
            return null;
        }
        String key = LearningTreeRedisConstant.gateJudgmentKey(domainId, goal);
        if (useRedis()) {
            try {
                String json = stringRedisTemplate.opsForValue().get(key);
                if (json != null && !json.isBlank()) {
                    return objectMapper.readValue(json, Judgment.class);
                }
            } catch (Exception e) {
                log.debug("Redis judgment get failed, try memory: {}", e.getMessage());
            }
        }
        return memoryJudgments.get(key);
    }

    public void cacheJudgment(Long domainId, String goal, Judgment judgment) {
        if (domainId == null || goal == null || goal.isBlank() || judgment == null) {
            return;
        }
        String key = LearningTreeRedisConstant.gateJudgmentKey(domainId, goal);
        if (useRedis()) {
            try {
                String json = objectMapper.writeValueAsString(judgment);
                stringRedisTemplate.opsForValue().set(key, json, JUDGMENT_TTL_SECONDS, TimeUnit.SECONDS);
                return;
            } catch (JsonProcessingException e) {
                log.warn("Redis judgment serialize failed, fallback memory: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("Redis judgment cache failed, fallback memory: {}", e.getMessage());
            }
        }
        memoryJudgments.put(key, judgment);
    }

    public void clearJudgment(Long domainId, String goal) {
        if (domainId == null || goal == null || goal.isBlank()) {
            return;
        }
        String key = LearningTreeRedisConstant.gateJudgmentKey(domainId, goal);
        if (useRedis()) {
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.debug("Redis judgment delete failed: {}", e.getMessage());
            }
        }
        memoryJudgments.remove(key);
    }

    /** Visible for tests */
    void clearAll() {
        memoryStore.clear();
        memoryJudgments.clear();
    }

    private static <V> Map<String, V> boundedMemoryMap() {
        return Collections.synchronizedMap(new LinkedHashMap<String, V>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > MEMORY_MAX_ENTRIES;
            }
        });
    }

    /** 门闩判定缓存快照（不含 passId，签发 pass 时重新生成） */
    public record Judgment(Long domainId, String goal, boolean related, String intent, String reason,
                           String answer, List<String> hints, Double confidence,
                           Long suggestedBranchId, String suggestedBranchTitle, Long suggestedParentBranchId) {
    }
}
