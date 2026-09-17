package com.ai.agent.model;

/**
 * Agent 工具风险分级，决定网关的放行策略：
 * L0 只读查询直接放行；L1 低风险写操作（创建草稿、保存日记等）放行；
 * L2 高风险操作（发布、删除等）需用户确认流程，当前由 agentL2Enabled 开关拦截。
 */
public enum AgentToolLevel {

    L0,
    L1,
    L2
}
