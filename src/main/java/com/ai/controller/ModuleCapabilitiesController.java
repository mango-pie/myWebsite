package com.ai.controller;

import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.config.ModuleEnablementService;
import com.ai.model.vo.ModuleCapabilitiesVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务模块开关能力查询（供前端菜单/设置 Tab 隐藏）。
 */
@RestController
@RequestMapping("/app")
public class ModuleCapabilitiesController {

    @Resource
    private ModuleEnablementService moduleEnablementService;

    @GetMapping("/modules")
    public BaseResponse<ModuleCapabilitiesVO> listModules() {
        return ResultUtils.success(moduleEnablementService.toVo());
    }
}
