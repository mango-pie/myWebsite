package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.study.StudyHabitAddRequest;
import com.ai.model.dto.study.StudyHabitCheckRequest;
import com.ai.model.dto.study.StudyHabitUpdateRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.study.StudyHabitVO;
import com.ai.service.StudyHabitService;
import com.ai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/study/habit")
public class StudyHabitController {

    @Autowired
    private StudyHabitService studyHabitService;

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Long> addHabit(@RequestBody StudyHabitAddRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyHabitService.addHabit(request, loginUser.getId()));
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> updateHabit(@RequestBody StudyHabitUpdateRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyHabitService.updateHabit(request, loginUser.getId()));
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> deleteHabit(@RequestBody DeleteRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyHabitService.deleteHabit(request.getId(), loginUser.getId()));
    }

    @GetMapping("/list/all")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<List<StudyHabitVO>> listAllHabits(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyHabitService.listAllHabits(loginUser.getId()));
    }

    @PostMapping("/check")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> checkHabit(@RequestBody StudyHabitCheckRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getHabitId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyHabitService.checkHabit(request, loginUser.getId()));
    }

    @PostMapping("/uncheck")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> uncheckHabit(@RequestBody StudyHabitCheckRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getHabitId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyHabitService.uncheckHabit(request, loginUser.getId()));
    }

    @GetMapping("/check/calendar")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<List<String>> getCheckCalendar(
            Long habitId, int year, int month, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(habitId == null || habitId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyHabitService.getCheckCalendar(habitId, loginUser.getId(), year, month));
    }
}
