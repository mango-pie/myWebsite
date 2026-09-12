package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.worklog.WorkLogImportRequest;
import com.ai.model.dto.worklog.WorkLogSaveRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.worklog.WorkLogEntryVO;
import com.ai.model.vo.worklog.WorkLogImportResultVO;
import com.ai.service.UserService;
import com.ai.service.WorkLogEntryService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EchoBot 工作日志桥接（Device Token）。
 */
@ConditionalOnModule("worklog")
@RestController
@RequestMapping("/pet/worklog")
public class PetWorkLogController {

    @Resource
    private WorkLogEntryService workLogEntryService;

    @Resource
    private UserService userService;

    @GetMapping("/today")
    public BaseResponse<WorkLogEntryVO> today(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(workLogEntryService.getByDate(LocalDate.now(), user.getId()));
    }

    @GetMapping("/by-date")
    public BaseResponse<WorkLogEntryVO> byDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(date == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(workLogEntryService.getByDate(date, user.getId()));
    }

    @GetMapping("/list")
    public BaseResponse<List<WorkLogEntryVO>> list(
            @RequestParam(defaultValue = "60") int limit,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(workLogEntryService.listRecent(user.getId(), limit));
    }

    @PostMapping("/save")
    public BaseResponse<Long> save(@RequestBody WorkLogSaveRequest body, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        if (body.getWorkDate() == null) {
            body.setWorkDate(LocalDate.now());
        }
        return ResultUtils.success(workLogEntryService.saveEntry(body, user.getId()));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(body == null || body.get("date") == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(workLogEntryService.deleteByDate(LocalDate.parse(body.get("date")), user.getId()));
    }

    @PostMapping("/import")
    public BaseResponse<WorkLogImportResultVO> importEntries(
            @RequestBody WorkLogImportRequest body,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(workLogEntryService.importEntries(body, user.getId()));
    }
}
