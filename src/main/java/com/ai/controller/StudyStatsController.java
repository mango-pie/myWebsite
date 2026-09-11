package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.entity.User;
import com.ai.model.vo.study.StudyRangeStatsVO;
import com.ai.model.vo.study.StudyTodayStatsVO;
import com.ai.service.StudyStatsService;
import com.ai.service.UserService;
import com.ai.setting.runtime.StudyRuntimeSettings;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@ConditionalOnModule("study")
@RestController
@RequestMapping("/study/stats")
public class StudyStatsController {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    private StudyStatsService studyStatsService;

    @Autowired
    private UserService userService;

    @Autowired
    private StudyRuntimeSettings studyRuntimeSettings;

    @GetMapping("/today")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyTodayStatsVO> getTodayStats(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyStatsService.getTodayStats(loginUser.getId()));
    }

    @GetMapping("/range")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyRangeStatsVO> getRangeStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        LocalDate end = parseOrToday(endDate);
        LocalDate start;
        if (startDate == null || startDate.isBlank()) {
            int days = Math.max(1, studyRuntimeSettings.statsDefaultRangeDays());
            start = end.minusDays(days - 1L);
        } else {
            start = LocalDate.parse(startDate.trim(), ISO_DATE);
        }
        ThrowUtils.throwIf(start.isAfter(end), ErrorCode.PARAMS_ERROR, "startDate 不能晚于 endDate");
        return ResultUtils.success(studyStatsService.getRangeStats(
                loginUser.getId(), start.format(ISO_DATE), end.format(ISO_DATE)));
    }

    private LocalDate parseOrToday(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(date.trim(), ISO_DATE);
    }
}
