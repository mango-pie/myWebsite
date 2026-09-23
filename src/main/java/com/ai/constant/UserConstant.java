package com.ai.constant;

public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    /**
     * Agent 令牌登录态键（AgentAuthFilter 写入 request 属性，仅当次请求有效）
     */
    String AGENT_LOGIN_STATE = "agent_login_user";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    /**
     * 高级管理员角色
     */
    String ADMINISTRATOR_ROLE = "administrator";
}
