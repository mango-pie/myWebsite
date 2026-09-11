package com.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.ai.constant.OpsAuditActionConstant;
import com.ai.constant.SiteSettingConstant;
import com.ai.constant.UserConstant;
import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.mapper.platform.UserMapper;
import com.ai.model.dto.user.UserQueryRequest;
import com.ai.model.entity.User;
import com.ai.model.enums.UserRoleEnum;
import com.ai.model.vo.LoginUserVO;
import com.ai.model.vo.UserVO;
import com.ai.service.OpsAuditLogService;
import com.ai.service.SiteSettingService;
import com.ai.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ai.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>  implements UserService{

    @Resource
    private SiteSettingService siteSettingService;

    @Resource
    private OpsAuditLogService opsAuditLogService;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        boolean registerEnabled = siteSettingService.getBool(
                SiteSettingConstant.MODULE_SECURITY, "register_enabled", true);
        if (!registerEnabled) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "暂未开放注册");
        }
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 检查是否重复
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.mapper.selectCountByQuery(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
        }
        // 3. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 插入数据
        String defaultRole = siteSettingService.getString(
                SiteSettingConstant.MODULE_SECURITY, "default_user_role", UserConstant.DEFAULT_ROLE);
        if (!UserConstant.DEFAULT_ROLE.equals(defaultRole)) {
            defaultRole = UserConstant.DEFAULT_ROLE;
        }
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName("无名");
        user.setUserRole(defaultRole);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    /**
     * 加密密码
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yupi";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }


    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            auditLoginFail(userAccount, "params_empty", request);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            auditLoginFail(userAccount, "account_invalid", request);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            auditLoginFail(userAccount, "password_invalid", request);
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        boolean genericHint = siteSettingService.getBool(
                SiteSettingConstant.MODULE_SECURITY, "login_fail_hint_generic", true);
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        QueryWrapper accountQuery = new QueryWrapper();
        accountQuery.eq("userAccount", userAccount);
        User accountUser = this.mapper.selectOneByQuery(accountQuery);
        if (accountUser == null) {
            auditLoginFail(userAccount, "user_not_found", request);
            if (genericHint) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在");
        }
        if (!encryptPassword.equals(accountUser.getUserPassword())) {
            auditLoginFail(userAccount, "password_mismatch", request);
            if (genericHint) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
            }
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 3. 记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, accountUser);
        opsAuditLogService.audit(
                OpsAuditActionConstant.USER_LOGIN_SUCCESS,
                accountUser.getId(),
                OpsAuditActionConstant.RESOURCE_USER,
                String.valueOf(accountUser.getId()),
                true,
                Map.of("userAccount", userAccount),
                request);
        // 4. 获得脱敏后的用户信息
        return this.getLoginUserVO(accountUser);
    }

    private void auditLoginFail(String userAccount, String reason, HttpServletRequest request) {
        opsAuditLogService.audit(
                OpsAuditActionConstant.USER_LOGIN_FAIL,
                null,
                OpsAuditActionConstant.RESOURCE_USER,
                null,
                false,
                Map.of("userAccount", StrUtil.blankToDefault(userAccount, ""), "reason", reason),
                request);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接返回上述结果）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        Long operatorId = userObj instanceof User user ? user.getId() : null;
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        opsAuditLogService.audit(
                OpsAuditActionConstant.USER_LOGOUT,
                operatorId,
                OpsAuditActionConstant.RESOURCE_USER,
                operatorId == null ? null : String.valueOf(operatorId),
                true,
                Map.of(),
                request);
        return true;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .eq("userRole", userRole)
                .like("userAccount", userAccount)
                .like("userName", userName)
                .like("userProfile", userProfile)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }
    // 在 UserServiceImpl 类中添加 isAdmin 方法实现
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }
}
