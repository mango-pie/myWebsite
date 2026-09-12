package com.ai.controller;

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
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/study/stats")
public class StudyStatsController {

    @Autowired
    private StudyStatsService studyStatsService;

    @Autowired
    private UserService userService;

    @GetMapping("/today")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyTodayStatsVO> getTodayStats(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyStatsService.getTodayStats(loginUser.getId()));
    }

    @GetMapping("/range")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyRangeStatsVO> getRangeStats(
            @RequestParam String startDate,
            @RequestParam String endDate,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(startDate == null || endDate == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyStatsService.getRangeStats(loginUser.getId(), startDate, endDate));
    }
}
