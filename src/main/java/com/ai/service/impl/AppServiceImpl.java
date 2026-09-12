package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.config.AppDeployProperties;
import com.ai.core.AiCodeGeneratorFacade;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.exception.ThrowUtils;
import com.ai.model.dto.app.AppAddRequest;
import com.ai.model.dto.app.AppQueryRequest;
import com.ai.model.dto.app.AppUpdateRequest;
import com.ai.model.entity.App;
import com.ai.model.entity.User;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.model.enums.MessageTypeEnum;
import com.ai.model.vo.AppVO;
import com.ai.model.vo.UserVO;
import com.ai.service.AppService;
import com.ai.service.ChatHistoryService;
import com.ai.service.UserService;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 应用 服务实现层。
 *
 */
@Service
public class AppServiceImpl extends ServiceImpl<com.ai.mapper.AppMapper, App> implements AppService {

    @Resource
    private UserService userService;

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private AppDeployProperties appDeployProperties;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Override
    public Long addApp(AppAddRequest appAddRequest, Long userId) {
        if (appAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String appName = appAddRequest.getAppName();
        String initPrompt = appAddRequest.getInitPrompt();
        if (StrUtil.hasBlank(appName, initPrompt)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用名称或初始化prompt不能为空");
        }
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("appName", appName);
        queryWrapper.eq("userId", userId);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用名称已存在");
        }
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(userId);
        app.setCreateTime(LocalDateTime.now());
        app.setUpdateTime(LocalDateTime.now());
        app.setEditTime(LocalDateTime.now());
        boolean saveResult = this.save(app);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建应用失败");
        }
        return app.getId();
    }

    @Override
    public boolean deleteApp(Long id, Long userId, boolean isAdmin) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        App app = this.getById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!isAdmin && !app.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 删除应用的所有对话历史
        chatHistoryService.deleteByAppId(id);
        return this.removeById(id);
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, Long userId, boolean isAdmin) {
        if (appUpdateRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long id = appUpdateRequest.getId();
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        App app = this.getById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!isAdmin && !app.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        String newAppName = appUpdateRequest.getAppName();
        if (StrUtil.isNotBlank(newAppName) && !newAppName.equals(app.getAppName())) {
            QueryWrapper queryWrapper = new QueryWrapper();
            queryWrapper.eq("appName", newAppName);
            queryWrapper.eq("userId", app.getUserId());
            queryWrapper.ne("id", id);
            long count = this.mapper.selectCountByQuery(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "应用名称已存在");
            }
        }
        BeanUtil.copyProperties(appUpdateRequest, app);
        app.setUpdateTime(LocalDateTime.now());
        app.setEditTime(LocalDateTime.now());
        return this.updateById(app);
    }

    @Override
    public AppVO getAppById(Long id, Long userId, boolean isAdmin) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        System.out.println("id:::"+id);
        App app = this.getById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        if (!isAdmin && !app.getUserId().equals(userId) && app.getPriority() == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return getAppVO(app);
    }


    @Override
    public Page<AppVO> listMyAppByPage(AppQueryRequest appQueryRequest, Long userId) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        if (pageNum <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页码不能小于等于0");
        }
        if (pageSize > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过20");
        }
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userId", userId);
        String appName = appQueryRequest.getAppName();
        if (appName != null) {
            queryWrapper.like("appName", appName);
        }
        String codeGenType = appQueryRequest.getCodeGenType();
        if (codeGenType != null) {
            queryWrapper.eq("codeGenType", codeGenType);
        }
        Page<App> appPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        return convertToVOPage(appPage);
    }

    @Override
    public Page<AppVO> listFeaturedAppByPage(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        if (pageSize > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过20");
        }
        QueryWrapper queryWrapper = getQueryWrapper(appQueryRequest);
        queryWrapper.isNotNull("priority");
        queryWrapper.gt("priority", 0);
        queryWrapper.orderBy("priority", false);
        queryWrapper.orderBy("createTime", false);
        Page<App> appPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        return convertToVOPage(appPage);
    }

    @Override
    public Page<AppVO> listAppByPageForAdmin(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        int pageNum = appQueryRequest.getPageNum();
        int pageSize = appQueryRequest.getPageSize();
        if (pageNum <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页码不能小于等于0");
        }
        if (pageSize > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过20");
        }
        QueryWrapper queryWrapper = new QueryWrapper();
        Long userId = appQueryRequest.getUserId();
        if (userId != null) {
            queryWrapper.eq("userId", userId);
        }
        String appName = appQueryRequest.getAppName();
        if (appName != null) {
            queryWrapper.like("appName", appName);
        }
        String codeGenType = appQueryRequest.getCodeGenType();
        if (codeGenType != null) {
            queryWrapper.eq("codeGenType", codeGenType);
        }
        queryWrapper.orderBy("createTime", false);
        Page<App> appPage = this.page(new Page<>(pageNum, pageSize), queryWrapper);
        return convertToVOPage(appPage);
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        User user = userService.getById(app.getUserId());
        if (user != null) {
            UserVO userVO = new UserVO();
            BeanUtil.copyProperties(user, userVO);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVO(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        return appList.stream().map(this::getAppVO).collect(Collectors.toList());
    }

    private QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        QueryWrapper queryWrapper = new QueryWrapper();
        if (appQueryRequest == null) {
            return queryWrapper;
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String codeGenType = appQueryRequest.getCodeGenType();
        Long userId = appQueryRequest.getUserId();
        queryWrapper.eq( "id", id);
        queryWrapper.like( "appName", appName);
        queryWrapper.eq( "codeGenType", codeGenType);
        queryWrapper.eq( "userId", userId);
        return queryWrapper;
    }

    private Page<AppVO> convertToVOPage(Page<App> appPage) {
        List<App> appList = appPage.getRecords();
        List<AppVO> appVOList = getAppVO(appList);
        Page<AppVO> appVOPage = new Page<>(appPage.getPageNumber(), appPage.getPageSize(), appPage.getTotalRow());
        appVOPage.setRecords(appVOList);
        return appVOPage;
    }


    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "用户消息不能为空");
        // 2. 查询应用信息
        App app = this.getById(appId);
        System.out.println("app: " + app);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限访问该应用，仅本人可以生成代码
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
//        // 4. 获取应用的代码生成类型
//        String codeGenTypeStr = app.getCodeGenType();
//
//        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
//        System.out.println("codeGenTypeEnum: " + codeGenTypeEnum);
//        if (codeGenTypeEnum == null) {
//            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
//        }

        // 4. 获取应用的代码生成类型
        String codeGenTypeStr = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenTypeStr);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的代码生成类型");
        }
        // 5. 通过校验后，添加用户消息到对话历史
        chatHistoryService.addChatMessage(appId, message, MessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 调用 AI 生成代码（流式）
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 7. 收集AI响应内容并在完成后记录到对话历史
        StringBuilder aiResponseBuilder = new StringBuilder();
        return contentFlux
                .map(chunk -> {
                    // 收集AI响应内容
                    aiResponseBuilder.append(chunk);
                    return chunk;
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    if (StrUtil.isNotBlank(aiResponse)) {
                        chatHistoryService.addChatMessage(appId, aiResponse, MessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, MessageTypeEnum.AI.getValue(), loginUser.getId());
                });

//        // 5. 调用 AI 生成代码
//        return aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
    }


    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        System.out.println("appId: " + appId);
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = appDeployProperties.resolveCodeOutputDir() + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. 复制文件到部署目录
        String deployDirPath = appDeployProperties.resolveCodeDeployDir() + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回可访问的 URL
        String host = appDeployProperties.getHost().replaceAll("/+$", "");
        return String.format("%s/%s/", host, deployKey);
    }

    /**
     * 删除应用时关联删除对话历史
     *
     * @param id 应用ID
     * @return 是否成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        // 转换为 Long 类型
        Long appId = Long.valueOf(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            // 记录日志但不阻止应用删除
            System.out.println("删除应用关联对话历史失败: " + e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }

}