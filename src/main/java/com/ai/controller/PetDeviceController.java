package com.ai.controller;

import com.ai.config.ConditionalOnModule;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.model.dto.pet.PetDeviceCreateRequest;
import com.ai.model.dto.pet.PetDeviceRevokeRequest;
import com.ai.model.entity.User;
import com.ai.model.vo.pet.PetDeviceVO;
import com.ai.service.UserService;
import com.ai.service.pet.PetDeviceService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * EchoBot 桌宠设备绑定。
 */
@ConditionalOnModule("pet")
@RestController
@RequestMapping("/pet")
public class PetDeviceController {

    @Resource
    private PetDeviceService petDeviceService;

    @Resource
    private UserService userService;

    @GetMapping("/devices")
    public BaseResponse<List<PetDeviceVO>> listDevices(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petDeviceService.list(loginUser.getId()));
    }

    @PostMapping("/devices")
    public BaseResponse<PetDeviceVO> bindDevice(@RequestBody PetDeviceCreateRequest createRequest,
                                                HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petDeviceService.bind(createRequest, loginUser.getId()));
    }

    @PostMapping("/devices/revoke")
    public BaseResponse<Boolean> revokeDevice(@RequestBody PetDeviceRevokeRequest revokeRequest,
                                              HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(petDeviceService.revoke(revokeRequest == null ? null : revokeRequest.getId(),
                loginUser.getId()));
    }
}
