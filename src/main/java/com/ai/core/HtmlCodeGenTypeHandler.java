package com.ai.core;

import com.ai.core.parser.HtmlCodeParser;
import com.ai.model.ai.HtmlCodeResult;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.saver.HtmlCodeFileSaverTemplate;
import com.ai.service.AiCodeGeneratorService;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * HTML 单文件模式的生成/解析/保存三元组。
 */
public class HtmlCodeGenTypeHandler implements CodeGenTypeHandler {

    private final HtmlCodeParser parser = new HtmlCodeParser();

    private final HtmlCodeFileSaverTemplate saver = new HtmlCodeFileSaverTemplate();

    @Override
    public CodeGenTypeEnum type() {
        return CodeGenTypeEnum.HTML;
    }

    @Override
    public HtmlCodeResult generate(AiCodeGeneratorService service, String userMessage) {
        return service.generateHtmlCode(userMessage);
    }

    @Override
    public Flux<String> generateStream(AiCodeGeneratorService service, String userMessage) {
        return service.generateHtmlCodeStream(userMessage);
    }

    @Override
    public HtmlCodeResult parse(String completeCode) {
        return parser.parseCode(completeCode);
    }

    @Override
    public File save(Object parsedResult, Long appId) {
        return saver.saveCode((HtmlCodeResult) parsedResult, appId);
    }
}
