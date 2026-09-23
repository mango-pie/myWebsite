package com.ai.core;

import com.ai.core.parser.MultiFileCodeParser;
import com.ai.model.ai.MultiFileCodeResult;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.saver.MultiFileCodeFileSaverTemplate;
import com.ai.service.AiCodeGeneratorService;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * 多文件模式的生成/解析/保存三元组。
 */
public class MultiFileCodeGenTypeHandler implements CodeGenTypeHandler {

    private final MultiFileCodeParser parser = new MultiFileCodeParser();

    private final MultiFileCodeFileSaverTemplate saver = new MultiFileCodeFileSaverTemplate();

    @Override
    public CodeGenTypeEnum type() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    public MultiFileCodeResult generate(AiCodeGeneratorService service, String userMessage) {
        return service.generateMultiFileCode(userMessage);
    }

    @Override
    public Flux<String> generateStream(AiCodeGeneratorService service, String userMessage) {
        return service.generateMultiFileCodeStream(userMessage);
    }

    @Override
    public MultiFileCodeResult parse(String completeCode) {
        return parser.parseCode(completeCode);
    }

    @Override
    public File save(Object parsedResult, Long appId) {
        return saver.saveCode((MultiFileCodeResult) parsedResult, appId);
    }
}
