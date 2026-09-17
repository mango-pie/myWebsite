package com.ai.agent.registry;

/**
 * Agent 工具模块抽象：每个业务域（博客/日记/学习…）实现一个子类，
 * 在 {@link #registerTools} 中用 builder 声明本域工具；
 * 配合 {@code @ConditionalOnModule} 实现模块关闭时工具整体下线。
 */
public abstract class AgentToolModule {

    public abstract String moduleName();

    protected abstract void registerTools(AgentToolRegistryBuilder registry);
}
