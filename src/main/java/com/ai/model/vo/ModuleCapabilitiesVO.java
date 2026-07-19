package com.ai.model.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ModuleCapabilitiesVO {

    private Map<String, Boolean> modules = new LinkedHashMap<>();
}
