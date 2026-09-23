package com.ai.core;

import com.ai.config.ConditionalOnModule;

import com.ai.exception.BusinessException;
import com.ai.exception.ErrorCode;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.service.AiCodeGeneratorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成外观类，组合生成和保存功能。
 * 类型分发统一走 {@link CodeGenTypeRegistry}，本类不再关心具体生成类型的差异。
 */
@Slf4j
@ConditionalOnModule({"app-lab", "chat"})
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService service = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        CodeGenTypeHandler handler = CodeGenTypeRegistry.require(codeGenTypeEnum);
        Object result = handler.generate(service, userMessage);
        return handler.save(result, appId);
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        AiCodeGeneratorService service = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        CodeGenTypeHandler handler = CodeGenTypeRegistry.require(codeGenTypeEnum);
        Flux<String> codeStream = handler.generateStream(service, userMessage);
        return processCodeStream(codeStream, handler, appId);
    }

    /**
     * 通用流式代码处理方法：收集完整代码后按类型解析并保存
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeHandler handler, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream.doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try {
                        Object parsedResult = handler.parse(codeBuilder.toString());
                        File savedDir = handler.save(parsedResult, appId);
                        log.info("保存成功，路径为：{}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("保存失败: {}", e.getMessage());
                    }
                });
    }
}
