package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.model.entity.User;
import com.ai.model.vo.study.StudyWorkspaceVO;
import com.ai.service.StudyWorkspaceService;
import com.ai.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/study/workspace")
public class StudyWorkspaceController {

    @Autowired
    private StudyWorkspaceService studyWorkspaceService;

    @Autowired
    private UserService userService;

    @GetMapping("/init")
    @AuthCheck(mustRole = UserConstant.ADMINISTRATOR_ROLE)
    public BaseResponse<StudyWorkspaceVO> initWorkspace(
            @RequestParam(defaultValue = "false") boolean createThemeLists,
            HttpServletRequest httpRequest) {
        User loginUser = userService.getLoginUser(httpRequest);
        return ResultUtils.success(studyWorkspaceService.initWorkspace(loginUser.getId(), createThemeLists));
    }
}
