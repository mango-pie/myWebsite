package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.study.StudyFocusIdRequest;
import com.ai.model.dto.study.StudyFocusStartRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.study.StudyFocusSessionVO;
import com.ai.service.StudyFocusSessionService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/study/focus")
public class StudyFocusController {

    @Autowired
    private StudyFocusSessionService studyFocusSessionService;

    @Autowired
    private UserService userService;

    @PostMapping("/start")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyFocusSessionVO> startFocus(@RequestBody StudyFocusStartRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyFocusSessionService.startFocus(request, loginUser.getId()));
    }

    @PostMapping("/pause")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyFocusSessionVO> pauseFocus(@RequestBody StudyFocusIdRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyFocusSessionService.pauseFocus(request, loginUser.getId()));
    }

    @PostMapping("/resume")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyFocusSessionVO> resumeFocus(@RequestBody StudyFocusIdRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyFocusSessionService.resumeFocus(request, loginUser.getId()));
    }

    @PostMapping("/complete")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyFocusSessionVO> completeFocus(@RequestBody StudyFocusIdRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyFocusSessionService.completeFocus(request, loginUser.getId()));
    }

    @PostMapping("/abandon")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyFocusSessionVO> abandonFocus(@RequestBody StudyFocusIdRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyFocusSessionService.abandonFocus(request, loginUser.getId()));
    }

    @GetMapping("/active")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyFocusSessionVO> getActiveFocus(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyFocusSessionService.getActiveFocus(loginUser.getId()));
    }

    @GetMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Page<StudyFocusSessionVO>> listFocusPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(pageSize > 100, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyFocusSessionService.listFocusPage(
                loginUser.getId(), pageNum, pageSize, startDate, endDate));
    }
}
