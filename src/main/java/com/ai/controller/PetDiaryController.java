package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ConditionalOnModule;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryVO;
import com.ai.service.DiaryEntryService;
import com.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EchoBot 日记桥接。
 */
@ConditionalOnModule("diary")
@RestController
@RequestMapping("/pet/diary")
public class PetDiaryController {

    @Resource
    private DiaryEntryService diaryEntryService;

    @Resource
    private UserService userService;

    @GetMapping("/today")
    public BaseResponse<DiaryEntryVO> today(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        DiaryEntryVO vo = diaryEntryService.getByDate(LocalDate.now(), user.getId());
        return ResultUtils.success(vo);
    }

    @GetMapping("/by-date")
    public BaseResponse<DiaryEntryVO> byDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(date == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(diaryEntryService.getByDate(date, user.getId()));
    }

    @PostMapping("/save")
    public BaseResponse<Long> save(@RequestBody DiaryEntrySaveRequest body, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(body == null, ErrorCode.PARAMS_ERROR);
        if (body.getDiaryDate() == null) {
            body.setDiaryDate(LocalDate.now());
        }
        long id = diaryEntryService.saveDiaryEntry(body, user.getId());
        return ResultUtils.success(id);
    }

    @GetMapping("/month")
    public BaseResponse<List<DiaryEntryMonthItemVO>> month(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(diaryEntryService.listByMonth(year, month, user.getId()));
    }
}
