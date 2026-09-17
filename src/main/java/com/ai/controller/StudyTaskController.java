package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.study.*;
import com.ai.model.entity.User;
import com.ai.model.vo.study.StudyBlogSyncVO;
import com.ai.model.vo.study.StudyTaskVO;
import com.ai.service.StudyTaskService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 学习任务接口：任务的增删改查、完成切换、排序与跨清单移动，
 * 支持多种视图分页查询（今日/清单/日历等），另含一键同步博客草稿为学习任务。
 * 当前所有接口仅管理员可用；study 模块关闭时本控制器不注册。
 */
@ConditionalOnModule("study")
@RestController
@RequestMapping("/study/task")
public class StudyTaskController {

    @Autowired
    private StudyTaskService studyTaskService;

    @Autowired
    private UserService userService;

    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Long> addTask(@RequestBody StudyTaskAddRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.addTask(request, loginUser.getId()));
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> updateTask(@RequestBody StudyTaskUpdateRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.updateTask(request, loginUser.getId()));
    }

    @PostMapping("/toggle")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> toggleTask(@RequestBody StudyTaskToggleRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.toggleTask(request, loginUser.getId()));
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> deleteTask(@RequestBody DeleteRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.deleteTask(request.getId(), loginUser.getId()));
    }

    @GetMapping("/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyTaskVO> getTaskVO(Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        StudyTaskVO vo = studyTaskService.getTaskVO(id, loginUser.getId());
        ThrowUtils.throwIf(vo == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(vo);
    }

    @GetMapping("/list/view")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Page<StudyTaskVO>> queryTaskView(StudyTaskViewQueryRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null || request.getView() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.queryTaskView(request, loginUser.getId()));
    }

    @PostMapping("/sort")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> sortTasks(@RequestBody StudyTaskSortRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.sortTasks(request, loginUser.getId()));
    }

    @PostMapping("/move")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<Boolean> moveTasks(@RequestBody StudyTaskMoveRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(studyTaskService.moveTasks(request, loginUser.getId()));
    }

    @PostMapping("/sync/blog/drafts")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyBlogSyncVO> syncBlogDrafts(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyTaskService.syncBlogDrafts(loginUser.getId()));
    }
}
