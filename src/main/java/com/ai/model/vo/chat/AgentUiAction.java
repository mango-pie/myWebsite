package com.ai.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUiAction implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;

    private String module;

    private String path;

    private String message;
}
