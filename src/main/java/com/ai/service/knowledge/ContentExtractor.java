package com.ai.service.knowledge;

public interface ContentExtractor {

    boolean supports(String inputType);

    String extract(String source);
}
