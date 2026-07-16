package com.ai.controller;

import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.dto.setting.IntegrationTestRequest;
import com.ai.model.dto.setting.SiteSettingUpdateRequest;
import com.ai.model.entity.User;
import com.ai.model.enums.UserRoleEnum;
import com.ai.model.vo.setting.IntegrationTestResultVO;
import com.ai.model.vo.setting.SettingModuleSchemaVO;
import com.ai.model.vo.setting.SettingModuleVO;
import com.ai.model.vo.setting.SettingModuleValuesVO;
import com.ai.model.vo.setting.SiteSettingAuditVO;
import com.ai.model.vo.setting.SiteSettingsBootstrapVO;
import com.ai.service.IntegrationConnectivityService;
import com.ai.service.SiteSettingService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 全站设置中心管理端 API（P0 + P1 + P5）。
 */
@RestController
@RequestMapping("/admin/site-settings")
public class AdminSiteSettingsController {

    @Resource
    private SiteSettingService siteSettingService;

    @Resource
    private IntegrationConnectivityService integrationConnectivityService;

    @Resource
    private UserService userService;

    @GetMapping("/modules")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<SettingModuleVO>> listModules() {
        return ResultUtils.success(siteSettingService.listModules());
    }

    @GetMapping("/bootstrap")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<SiteSettingsBootstrapVO> bootstrap(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        boolean admin = isAdmin(loginUser);
        return ResultUtils.success(siteSettingService.bootstrap(admin));
    }

    @GetMapping("/audit")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<SiteSettingAuditVO>> pageAudit(
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResultUtils.success(siteSettingService.pageAudit(module, pageNum, pageSize));
    }

    @GetMapping("/health")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<List<IntegrationTestResultVO>> healthAll() {
        return ResultUtils.success(integrationConnectivityService.testAll());
    }

    @PostMapping("/health/{target}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<IntegrationTestResultVO> healthOne(@PathVariable("target") String target) {
        return ResultUtils.success(integrationConnectivityService.test(target));
    }

    @GetMapping("/{module}/schema")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<SettingModuleSchemaVO> getSchema(@PathVariable("module") String module) {
        return ResultUtils.success(siteSettingService.getSchema(module));
    }

    @GetMapping("/{module}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<SettingModuleValuesVO> getValues(@PathVariable("module") String module) {
        return ResultUtils.success(siteSettingService.getModuleValues(module));
    }

    @PutMapping("/{module}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> update(@PathVariable("module") String module,
                                        @RequestBody SiteSettingUpdateRequest request,
                                        HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        siteSettingService.updateModule(module, request, loginUser.getId());
        return ResultUtils.success(true);
    }

    @PostMapping("/{module}/reset")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> reset(@PathVariable("module") String module,
                                       HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        siteSettingService.resetModule(module, loginUser.getId());
        return ResultUtils.success(true);
    }

    @PostMapping("/integration/test")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<IntegrationTestResultVO> testIntegration(@RequestBody IntegrationTestRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(integrationConnectivityService.test(request.getTarget()));
    }

    private boolean isAdmin(User user) {
        if (user == null) {
            return false;
        }
        UserRoleEnum role = UserRoleEnum.getEnumByValue(user.getUserRole());
        return UserRoleEnum.ADMIN.equals(role) || UserRoleEnum.ADMINISTRATOR.equals(role);
    }
}
