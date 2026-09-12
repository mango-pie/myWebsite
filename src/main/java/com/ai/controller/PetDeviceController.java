package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.pet.PetDeviceBindRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.LoginUserVO;
import com.ai.model.vo.pet.PetDeviceBindVO;
import com.ai.model.vo.pet.PetDeviceVO;
import com.ai.service.PetDeviceService;
import com.ai.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 桌宠设备绑定（网页 Session）与身份探测（Session 或 Device Token）。
 */
@RestController
@RequestMapping("/pet")
public class PetDeviceController {

    @Resource
    private PetDeviceService petDeviceService;

    @Resource
    private UserService userService;

    /** 当前身份（网站 Cookie 或 EchoBot Bearer 均可） */
    @GetMapping("/me")
    public BaseResponse<LoginUserVO> me(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(user));
    }

    /** 绑定新设备；明文 token 仅返回一次。必须网站 Session 登录。 */
    @PostMapping("/devices")
    public BaseResponse<PetDeviceBindVO> bind(@RequestBody(required = false) PetDeviceBindRequest body,
                                              HttpServletRequest request) {
        requireWebsiteSession(request);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petDeviceService.bind(loginUser.getId(),
                body != null ? body : new PetDeviceBindRequest()));
    }

    @GetMapping("/devices")
    public BaseResponse<List<PetDeviceVO>> list(HttpServletRequest request) {
        requireWebsiteSession(request);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petDeviceService.listByUser(loginUser.getId()));
    }

    @PostMapping("/devices/revoke")
    public BaseResponse<Boolean> revoke(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        requireWebsiteSession(request);
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(petDeviceService.revoke(loginUser.getId(), deleteRequest.getId()));
    }

    private void requireWebsiteSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(UserConstant.USER_LOGIN_STATE) instanceof User)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "请在网站登录后管理桌宠设备");
        }
    }
}
