package com.ai.core;

import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.service.AiCodeGeneratorService;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 代码生成类型处理器：一种生成类型对应生成/流式生成/解析/保存的一组实现，
 * 由 {@link CodeGenTypeRegistry} 分发，替代散落在 Facade 与 Executor 里的平行 switch。
 */
public interface CodeGenTypeHandler {

    CodeGenTypeEnum type();

    Object generate(AiCodeGeneratorService service, String userMessage);

    Flux<String> generateStream(AiCodeGeneratorService service, String userMessage);

    Object parse(String completeCode);

    File save(Object parsedResult, Long appId);
}
