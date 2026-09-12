package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.DiaryEntryMapper;
import com.ai.model.dto.diary.DiaryEntryQueryRequest;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.entity.DiaryEntry;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryPrevNextVO;
import com.ai.model.vo.diary.DiaryEntryVO;
import com.ai.service.DiaryEntryService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiaryEntryServiceImpl extends ServiceImpl<DiaryEntryMapper, DiaryEntry> implements DiaryEntryService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long saveDiaryEntry(DiaryEntrySaveRequest request, Long userId) {
        if (request == null || userId == null || request.getDiaryDate() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        String content = request.getContent();
        if (content == null) {
            content = "";
        }

        DiaryEntry existing = findActiveByUserAndDate(userId, request.getDiaryDate());
        LocalDateTime now = LocalDateTime.now();
        Integer status = request.getStatus() != null ? request.getStatus() : 0;

        if (existing != null) {
            existing.setTitle(request.getTitle());
            existing.setContent(content);
            existing.setMood(request.getMood());
            existing.setWeather(request.getWeather());
            existing.setTags(serializeTags(request.getTags()));
            existing.setStatus(status);
            existing.setCoverUrl(request.getCoverUrl());
            existing.setWordCount(calcWordCount(content));
            existing.setUpdatedTime(now);
            if (!this.updateById(existing)) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存日记失败");
            }
            return existing.getId();
        }

        DiaryEntry entry = DiaryEntry.builder()
                .userId(userId)
                .diaryDate(request.getDiaryDate())
                .title(request.getTitle())
                .content(content)
                .mood(request.getMood())
                .weather(request.getWeather())
                .tags(serializeTags(request.getTags()))
                .status(status)
                .coverUrl(request.getCoverUrl())
                .wordCount(calcWordCount(content))
                .extendInfo("{}")
                .createdTime(now)
                .updatedTime(now)
                .build();

        if (!this.save(entry)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存日记失败");
        }
        return entry.getId();
    }

    @Override
    public DiaryEntryVO getDiaryEntryVO(Long id, Long userId) {
        DiaryEntry entry = getActiveEntry(id, userId);
        return convertToVO(entry);
    }

    @Override
    public DiaryEntryVO getByDate(LocalDate date, Long userId) {
        if (date == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        DiaryEntry entry = findActiveByUserAndDate(userId, date);
        if (entry == null) {
            return null;
        }
        return convertToVO(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteDiaryEntry(Long id, Long userId) {
        DiaryEntry entry = getActiveEntry(id, userId);
        entry.setDeletedTime(LocalDateTime.now());
        entry.setUpdatedTime(LocalDateTime.now());
        return this.updateById(entry);
    }

    @Override
    public Page<DiaryEntryVO> queryPage(DiaryEntryQueryRequest request, Long userId) {
        if (request == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        int pageNum = request.getPageNum();
        int pageSize = request.getPageSize();
        if (pageSize > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_time");

        if (request.getStartDate() != null) {
            queryWrapper.ge("diary_date", request.getStartDate());
        }
        if (request.getEndDate() != null) {
            queryWrapper.le("diary_date", request.getEndDate());
        }
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }

        queryWrapper.orderBy("diary_date", false);

        Page<DiaryEntry> page = this.page(Page.of(pageNum, pageSize), queryWrapper);
        Page<DiaryEntryVO> voPage = new Page<>(pageNum, pageSize, page.getTotalRow());
        voPage.setRecords(page.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<DiaryEntryMonthItemVO> listByMonth(int year, int month, Long userId) {
        if (userId == null || year < 1970 || month < 1 || month > 12) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());

        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_time")
                .ge("diary_date", start)
                .le("diary_date", end)
                .orderBy("diary_date", true);

        List<DiaryEntry> entries = this.list(queryWrapper);
        if (CollUtil.isEmpty(entries)) {
            return Collections.emptyList();
        }

        return entries.stream().map(entry -> {
            DiaryEntryMonthItemVO item = new DiaryEntryMonthItemVO();
            item.setId(entry.getId());
            item.setDiaryDate(entry.getDiaryDate());
            item.setTitle(entry.getTitle());
            item.setMood(entry.getMood());
            item.setStatus(entry.getStatus());
            return item;
        }).collect(Collectors.toList());
    }

    @Override
    public DiaryEntryPrevNextVO getPrevNext(Long id, Long userId) {
        DiaryEntry current = getActiveEntry(id, userId);
        DiaryEntryPrevNextVO vo = new DiaryEntryPrevNextVO();

        QueryWrapper prevWrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_time")
                .lt("diary_date", current.getDiaryDate())
                .orderBy("diary_date", false)
                .limit(1);
        DiaryEntry prev = this.getOne(prevWrapper);
        if (prev != null) {
            vo.setPrevId(prev.getId());
            vo.setPrevDate(prev.getDiaryDate());
            vo.setPrevTitle(prev.getTitle());
        }

        QueryWrapper nextWrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .isNull("deleted_time")
                .gt("diary_date", current.getDiaryDate())
                .orderBy("diary_date", true)
                .limit(1);
        DiaryEntry next = this.getOne(nextWrapper);
        if (next != null) {
            vo.setNextId(next.getId());
            vo.setNextDate(next.getDiaryDate());
            vo.setNextTitle(next.getTitle());
        }

        return vo;
    }

    private DiaryEntry findActiveByUserAndDate(Long userId, LocalDate date) {
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("user_id", userId)
                .eq("diary_date", date)
                .isNull("deleted_time")
                .limit(1);
        return this.getOne(queryWrapper);
    }

    private DiaryEntry getActiveEntry(Long id, Long userId) {
        if (id == null || id <= 0 || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        DiaryEntry entry = this.getById(id);
        if (entry == null || entry.getDeletedTime() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "日记不存在");
        }
        if (!entry.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权访问此日记");
        }
        return entry;
    }

    private DiaryEntryVO convertToVO(DiaryEntry entry) {
        DiaryEntryVO vo = new DiaryEntryVO();
        BeanUtil.copyProperties(entry, vo);
        vo.setTags(parseTags(entry.getTags()));
        vo.setStatusText(entry.getStatus() != null && entry.getStatus() == 1 ? "完成" : "草稿");
        return vo;
    }

    private int calcWordCount(String content) {
        if (StrUtil.isBlank(content)) {
            return 0;
        }
        return content.replaceAll("\\s+", "").length();
    }

    private String serializeTags(List<String> tags) {
        if (CollUtil.isEmpty(tags)) {
            return null;
        }
        return JSONUtil.toJsonStr(tags);
    }

    private List<String> parseTags(String tagsJson) {
        if (StrUtil.isBlank(tagsJson)) {
            return Collections.emptyList();
        }
        try {
            return JSONUtil.toList(tagsJson, String.class);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
