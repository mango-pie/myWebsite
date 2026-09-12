package com.ai.agent;

import com.ai.agent.config.ChatAgentProperties;
import com.ai.agent.registry.AgentToolRegistry;
import com.ai.model.vo.chat.ChatAgentConfigVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatAgentConfigService {

    @Resource
    private ChatAgentProperties chatAgentProperties;

    @Resource
    private AgentToolRegistry agentToolRegistry;

    public ChatAgentConfigVO getConfig() {
        ChatAgentConfigVO vo = new ChatAgentConfigVO();
        vo.setDefaultMode(ChatMode.ASK.getValue());
        vo.setAgentEnabled(chatAgentProperties.isEnabled());
        vo.setAgentHint("Agent 模式可创建待办、博客草稿、保存日记等；不会自动发布或删除，除非后续开放确认流程。");
        vo.setModules(agentToolRegistry.getModuleNames());
        vo.setToolNames(agentToolRegistry.getDefinitions().stream()
                .map(def -> def.name())
                .toList());
        return vo;
    }
}
