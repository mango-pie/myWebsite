package com.ai.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ai.annotation.AuthCheck;
import com.ai.common.BaseResponse;
import com.ai.common.DeleteRequest;
import com.ai.common.PageRequest;
import com.ai.common.ResultUtils;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.app.AppAddRequest;
import com.ai.model.dto.app.AppDeployRequest;
import com.ai.model.dto.app.AppQueryRequest;
import com.ai.model.dto.app.AppUpdateRequest;
import com.ai.model.entity.App;
import com.ai.model.entity.User;
import com.ai.model.vo.AppVO;
import com.ai.model.vo.LoginUserVO;
import com.ai.service.AppService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 应用 控制层。
 */
@RestController
@RequestMapping("/app")
@Slf4j
public class AppController {

    @Resource
    private AppService appService;

    @Resource
    private UserService userService;

    /**
     * 创建应用
     *
     * @param appAddRequest 应用创建请求
     * @param request HTTP请求
     * @return 应用id
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);

        System.out.println("appAdd" + appAddRequest);
        User loginUser = userService.getLoginUser(request);
        Long appId = appService.addApp(appAddRequest, loginUser.getId());
        return ResultUtils.success(appId);
    }

    /**
     * 删除应用
     *
     * @param deleteRequest 删除请求
     * @param request HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        boolean result = appService.deleteApp(deleteRequest.getId(), loginUser.getId(), isAdmin);
        return ResultUtils.success(result);
    }

    /**
     * 更新应用
     *
     * @param appUpdateRequest 应用更新请求
     * @param request HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appUpdateRequest == null, ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        boolean result = appService.updateApp(appUpdateRequest, loginUser.getId(), isAdmin);
        return ResultUtils.success(result);
    }

    /**
     * 根据id获取应用详情
     *
     * @param id 应用id
     * @param request HTTP请求
     * @return 应用详情
     */
    @GetMapping("/get/{id}")
    public BaseResponse<AppVO> getAppById(@PathVariable Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        AppVO app = appService.getAppById(id, loginUser.getId(), isAdmin);
        return ResultUtils.success(app);
    }

    /**
     * 分页查询用户自己的应用列表
     *
     * @param appQueryRequest 查询条件
     * @param request HTTP请求
     * @return 分页结果
     */
    @PostMapping("/my/list/page")
    public BaseResponse<Page<AppVO>> listMyAppByPage(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        Page<AppVO> appPage = appService.listMyAppByPage(appQueryRequest, loginUser.getId());
        return ResultUtils.success(appPage);
    }

    /**
     * 分页查询精选应用列表
     *
     * @param appQueryRequest 查询条件
     * @return 分页结果
     */
    @PostMapping("/featured/list/page")
    public BaseResponse<Page<AppVO>> listFeaturedAppByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        Page<AppVO> appPage = appService.listFeaturedAppByPage(appQueryRequest);
        return ResultUtils.success(appPage);
    }

    /**
     * 管理员删除应用
     *
     * @param deleteRequest 删除请求
     * @param request HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null, ErrorCode.PARAMS_ERROR);

        boolean result = appService.deleteApp(deleteRequest.getId(), null, true);
        return ResultUtils.success(result);
    }

    /**
     * 管理员更新应用
     *
     * @param appUpdateRequest 应用更新请求
     * @param request HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appUpdateRequest == null, ErrorCode.PARAMS_ERROR);

        boolean result = appService.updateApp(appUpdateRequest, null, true);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询应用列表
     *
     * @param appQueryRequest 查询条件
     * @param request HTTP请求
     * @return 分页结果
     */
    @PostMapping("/list/page/admin")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppByPageForAdmin(@RequestBody AppQueryRequest appQueryRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);

        Page<AppVO> appPage = appService.listAppByPageForAdmin(appQueryRequest);
        return ResultUtils.success(appPage);
    }

    /**
     * 管理员根据id获取应用详情
     *
     * @param id 应用id
     * @param request HTTP请求
     * @return 应用详情
     */
    @GetMapping("/get/admin/{id}")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppByIdByAdmin(@PathVariable Long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);

        AppVO app = appService.getAppById(id, null, true);
        return ResultUtils.success(app);
    }

    /**
     * 应用聊天生成代码（流式 SSE）
     *
     * @param appId   应用 ID
     * @param message 用户消息
     * @param request 请求对象
     * @return 生成结果流
     */
//    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> chatToGenCode(@RequestParam Long appId,
//                                      @RequestParam String message,
//                                      HttpServletRequest request) {
//        // 参数校验
//        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
//        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
//        // 获取当前登录用户
//        User loginUser = userService.getLoginUser(request);
//        // 调用服务生成代码（流式）
//        return appService.chatToGenCode(appId, message, loginUser);
//    }

//    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
//                                                       @RequestParam String message,
//                                                       HttpServletRequest request) {
//        // 参数校验
//        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
//        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
//        // 获取当前登录用户
//        User loginUser = userService.getLoginUser(request);
//        // 调用服务生成代码（流式）
//        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);
//        // 转换为 ServerSentEvent 格式
//        return contentFlux
//                .map(chunk -> {
//                    // 将内容包装成JSON对象
//                    Map<String, String> wrapper = Map.of("d", chunk);
//                    String jsonData = JSONUtil.toJsonStr(wrapper);
//                    return ServerSentEvent.<String>builder()
//                            .data(jsonData)
//                            .build();
//                });
//    }

    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                                       @RequestParam String message,
                                                       HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID无效");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务生成代码（流式）
        Flux<String> contentFlux = appService.chatToGenCode(appId, message, loginUser);
        // 转换为 ServerSentEvent 格式
        return contentFlux
                .map(chunk -> {
                    // 将内容包装成JSON对象
                    Map<String, String> wrapper = Map.of("d", chunk);
                    String jsonData = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(jsonData)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ));
    }

    /**
     * 应用部署
     *
     * @param appDeployRequest 部署请求
     * @param request          请求
     * @return 部署 URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        System.out.println(appDeployRequest);
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        // 调用服务部署应用
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }


}