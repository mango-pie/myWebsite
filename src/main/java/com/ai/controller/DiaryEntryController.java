package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.diary.DiaryEntryQueryRequest;
import com.ai.model.dto.diary.DiaryEntrySaveRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.diary.DiaryEntryMonthItemVO;
import com.ai.model.vo.diary.DiaryEntryPrevNextVO;
import com.ai.model.vo.diary.DiaryEntryVO;
import com.ai.service.DiaryEntryService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/diary")
public class DiaryEntryController {

    @Autowired
    private DiaryEntryService diaryEntryService;

    @Autowired
    private UserService userService;

    @PostMapping("/save")
    public BaseResponse<Long> saveDiaryEntry(@RequestBody DiaryEntrySaveRequest request, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        long id = diaryEntryService.saveDiaryEntry(request, loginUser.getId());
        return ResultUtils.success(id);
    }

    @GetMapping("/get/vo")
    public BaseResponse<DiaryEntryVO> getDiaryEntryVO(@RequestParam Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        DiaryEntryVO vo = diaryEntryService.getDiaryEntryVO(id, loginUser.getId());
        return ResultUtils.success(vo);
    }

    @GetMapping("/get/by-date")
    public BaseResponse<DiaryEntryVO> getDiaryByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(date == null, ErrorCode.PARAMS_ERROR);
        DiaryEntryVO vo = diaryEntryService.getByDate(date, loginUser.getId());
        return ResultUtils.success(vo);
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteDiaryEntry(@RequestBody DeleteRequest deleteRequest, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = diaryEntryService.deleteDiaryEntry(deleteRequest.getId(), loginUser.getId());
        return ResultUtils.success(result);
    }

    @PostMapping("/list/page")
    public BaseResponse<Page<DiaryEntryVO>> queryDiaryPage(
            @RequestBody DiaryEntryQueryRequest request,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        Page<DiaryEntryVO> page = diaryEntryService.queryPage(request, loginUser.getId());
        return ResultUtils.success(page);
    }

    @GetMapping("/list/month")
    public BaseResponse<List<DiaryEntryMonthItemVO>> listDiaryByMonth(
            @RequestParam int year,
            @RequestParam int month,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        List<DiaryEntryMonthItemVO> list = diaryEntryService.listByMonth(year, month, loginUser.getId());
        return ResultUtils.success(list);
    }

    @GetMapping("/prev-next")
    public BaseResponse<DiaryEntryPrevNextVO> getDiaryPrevNext(@RequestParam Long id, HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        DiaryEntryPrevNextVO vo = diaryEntryService.getPrevNext(id, loginUser.getId());
        return ResultUtils.success(vo);
    }
}
