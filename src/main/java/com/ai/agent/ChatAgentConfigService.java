package com.ai.agent;

import com.ai.config.ConditionalOnModule;

import com.ai.agent.registry.AgentToolRegistry;
import com.ai.model.vo.chat.ChatAgentConfigVO;
import com.ai.setting.runtime.ChatRuntimeSettings;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * Agent 模式配置查询：给前端暴露当前 Agent 开关、默认模式、
 * 已注册的工具模块与工具名列表，用于聊天界面初始化与能力提示。
 */
@ConditionalOnModule("chat")
@Service
public class ChatAgentConfigService {

    @Resource
    private ChatRuntimeSettings chatRuntimeSettings;

    @Resource
    private AgentToolRegistry agentToolRegistry;

    public ChatAgentConfigVO getConfig() {
        ChatAgentConfigVO vo = new ChatAgentConfigVO();
        vo.setDefaultMode(ChatMode.ASK.getValue());
        vo.setAgentEnabled(chatRuntimeSettings.agentEnabled());
        vo.setAgentHint("Agent 模式可创建待办、博客草稿、保存日记等；不会自动发布或删除，除非后续开放确认流程。");
        vo.setModules(agentToolRegistry.getModuleNames());
        vo.setToolNames(agentToolRegistry.getDefinitions().stream()
                .map(def -> def.name())
                .toList());
        return vo;
    }
}
