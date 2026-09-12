package com.ai.service.impl;

import cn.hutool.json.JSONUtil;
import com.ai.constant.StudyConstant;
import com.ai.model.vo.study.StudyFocusSessionVO;
import com.ai.model.vo.study.StudyListVO;
import com.ai.model.vo.study.StudyTodayStatsVO;
import com.ai.service.StudyRedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
@Service
public class StudyRedisCacheServiceImpl implements StudyRedisCacheService {

    @Autowired(required = false)
    private StringRedisTemplate stringRedisTemplate;

    @Value("${app.redis.available:false}")
    private boolean redisAvailable;

    private boolean useRedis() {
        return redisAvailable && stringRedisTemplate != null;
    }

    @Override
    public StudyTodayStatsVO getTodayStats(Long userId, Supplier<StudyTodayStatsVO> loader) {
        if (!useRedis()) {
            return loader.get();
        }
        try {
            String key = StudyConstant.redisKey(userId, StudyConstant.KEY_TODAY_STATS);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSONUtil.toBean(cached, StudyTodayStatsVO.class);
            }
            StudyTodayStatsVO stats = loader.get();
            if (stats != null) {
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(stats),
                        StudyConstant.TODAY_STATS_TTL_SECONDS, TimeUnit.SECONDS);
            }
            return stats;
        } catch (Exception e) {
            log.debug("Redis getTodayStats fallback: {}", e.getMessage());
            return loader.get();
        }
    }

    @Override
    public void evictTodayStats(Long userId) {
        runRedisQuietly(() -> stringRedisTemplate.delete(
                StudyConstant.redisKey(userId, StudyConstant.KEY_TODAY_STATS)));
    }

    @Override
    public List<StudyListVO> getLists(Long userId, Supplier<List<StudyListVO>> loader) {
        if (!useRedis()) {
            return loader.get();
        }
        try {
            String key = StudyConstant.redisKey(userId, StudyConstant.KEY_LISTS);
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSONUtil.toList(cached, StudyListVO.class);
            }
            List<StudyListVO> lists = loader.get();
            if (lists != null) {
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(lists),
                        StudyConstant.LISTS_TTL_SECONDS, TimeUnit.SECONDS);
            }
            return lists;
        } catch (Exception e) {
            log.debug("Redis getLists fallback: {}", e.getMessage());
            return loader.get();
        }
    }

    @Override
    public void evictLists(Long userId) {
        runRedisQuietly(() -> stringRedisTemplate.delete(
                StudyConstant.redisKey(userId, StudyConstant.KEY_LISTS)));
    }

    @Override
    public StudyFocusSessionVO getActiveFocus(Long userId) {
        if (!useRedis()) {
            return null;
        }
        try {
            String cached = stringRedisTemplate.opsForValue().get(
                    StudyConstant.redisKey(userId, StudyConstant.KEY_ACTIVE_FOCUS));
            if (cached == null) {
                return null;
            }
            return JSONUtil.toBean(cached, StudyFocusSessionVO.class);
        } catch (Exception e) {
            log.debug("Redis getActiveFocus fallback: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void setActiveFocus(Long userId, StudyFocusSessionVO session) {
        if (!useRedis()) {
            return;
        }
        runRedisQuietly(() -> {
            String key = StudyConstant.redisKey(userId, StudyConstant.KEY_ACTIVE_FOCUS);
            if (session == null) {
                stringRedisTemplate.delete(key);
            } else {
                long ttlHours = session.getPlannedMinutes() != null ? session.getPlannedMinutes() + 60 : 120;
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(session), ttlHours, TimeUnit.MINUTES);
            }
        });
    }

    @Override
    public void evictActiveFocus(Long userId) {
        runRedisQuietly(() -> stringRedisTemplate.delete(
                StudyConstant.redisKey(userId, StudyConstant.KEY_ACTIVE_FOCUS)));
    }

    @Override
    public boolean tryInitLock(Long userId) {
        if (!useRedis()) {
            return true;
        }
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    StudyConstant.redisKey(userId, StudyConstant.KEY_INIT_LOCK),
                    "1",
                    StudyConstant.INIT_LOCK_TTL_SECONDS,
                    TimeUnit.SECONDS);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.debug("Redis tryInitLock fallback: {}", e.getMessage());
            return true;
        }
    }

    @Override
    public void releaseInitLock(Long userId) {
        runRedisQuietly(() -> stringRedisTemplate.delete(
                StudyConstant.redisKey(userId, StudyConstant.KEY_INIT_LOCK)));
    }

    private void runRedisQuietly(Runnable action) {
        if (!useRedis()) {
            return;
        }
        try {
            action.run();
        } catch (Exception e) {
            log.debug("Redis operation skipped: {}", e.getMessage());
        }
    }
}
