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
import com.ai.service.UserService;
import com.ai.service.pet.PetFacade;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Pet 日记能力接口。仅在 diary 模块启用时注册。
 * <p>
 * 鉴权走 {@link UserService#getLoginUser} 解析出的身份（当前为 Session）。
 * 后续 Device Bearer 过滤器可写入同一登录上下文，本 Controller 无需改 Session 判断。
 * 设备绑定管理（仅 Session）不在本 PR 范围。
 */
@ConditionalOnModule("diary")
@RestController
@RequestMapping("/pet/diary")
public class PetDiaryController {

    @Resource
    private PetFacade petFacade;

    @Resource
    private UserService userService;

    @GetMapping("/today")
    public BaseResponse<DiaryEntryVO> getToday(HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(petFacade.diaryToday(loginUser.getId()));
    }

    @GetMapping("/by-date")
    public BaseResponse<DiaryEntryVO> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(date == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(petFacade.diaryByDate(date, loginUser.getId()));
    }

    @PostMapping("/save")
    public BaseResponse<Long> save(@RequestBody DiaryEntrySaveRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(petFacade.diarySave(request, loginUser.getId()));
    }

    @GetMapping("/month")
    public BaseResponse<List<DiaryEntryMonthItemVO>> listByMonth(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(year < 1970 || month < 1 || month > 12, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(petFacade.diaryMonth(year, month, loginUser.getId()));
    }
}
