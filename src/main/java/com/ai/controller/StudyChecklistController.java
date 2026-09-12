package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.study.StudyChecklistAddRequest;
import com.ai.model.dto.study.StudyChecklistUpdateRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.study.StudyTaskChecklistVO;
import com.ai.service.StudyTaskChecklistService;
import com.ai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/study/checklist")
public class StudyChecklistController {

    @Autowired
    private StudyTaskChecklistService studyTaskChecklistService;

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Long> addChecklist(@RequestBody StudyChecklistAddRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskChecklistService.addChecklist(request, loginUser.getId()));
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> updateChecklist(@RequestBody StudyChecklistUpdateRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskChecklistService.updateChecklist(request, loginUser.getId()));
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> deleteChecklist(@RequestBody DeleteRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskChecklistService.deleteChecklist(request.getId(), loginUser.getId()));
    }

    @GetMapping("/list")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<List<StudyTaskChecklistVO>> listChecklist(Long taskId, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(taskId == null || taskId <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskChecklistService.listByTaskId(taskId, loginUser.getId()));
    }
}
