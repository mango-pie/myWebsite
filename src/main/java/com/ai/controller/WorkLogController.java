package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.model.dto.worklog.WorkLogDeleteRequest;
import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.worklog.WorkLogEntryVO;
import com.ai.model.vo.worklog.WorkLogImportResultVO;
import com.ai.service.UserService;
import com.ai.service.worklog.WorkLogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作日志云同步：本地 IndexedDB 为主存储，云端为跨设备备份。
 */
@ConditionalOnModule("worklog")
@RestController
@RequestMapping("/worklog")
public class WorkLogController {

    @Resource
    private WorkLogService workLogService;

    @Resource
    private UserService userService;

    @GetMapping("/list")
    public BaseResponse<List<WorkLogEntryVO>> list(@RequestParam(required = false) Integer limit,
                                                   HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workLogService.list(limit, loginUser.getId()));
    }

    @GetMapping("/by-date")
    public BaseResponse<WorkLogEntryVO> byDate(@RequestParam String date, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workLogService.byDate(date, loginUser.getId()));
    }

    @PostMapping("/save")
    public BaseResponse<WorkLogEntryVO> save(@RequestBody WorkLogSaveRequest saveRequest,
                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workLogService.save(saveRequest, loginUser.getId()));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody WorkLogDeleteRequest deleteRequest,
                                        HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workLogService.delete(deleteRequest.getDate(), loginUser.getId()));
    }

    @PostMapping("/import")
    public BaseResponse<WorkLogImportResultVO> importEntries(@RequestBody WorkLogImportRequest importRequest,
                                                             HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(workLogService.importEntries(importRequest, loginUser.getId()));
    }
}
