package com.ai;

import com.ai.model.ai.HtmlCodeResult;
import com.ai.model.enums.CodeGenTypeEnum;
import com.ai.service.AiCodeGeneratorService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import java.util.List;
import com.ai.core.AiCodeGeneratorFacade;

// 需要完整 Spring 上下文（DB/Redis 等），归类为 integration，默认在 CI/本地 mvn verify 中跳过。
// 需要时用 mvn -Dgroups=integration test 单独运行。
@Tag("integration")
@SpringBootTest
class AiApplicationTests {

    @Test
    void contextLoads() {

    }
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;
//    @Test
//    void generateAndSaveCodeStream() {
//        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("任务记录网站", CodeGenTypeEnum.MULTI_FILE, 1L);
//        // 阻塞等待所有数据收集完成
//        List<String> result = codeStream.collectList().block();
//        // 验证结果
//        Assertions.assertNotNull(result);
//        String completeContent = String.join("", result);
//        Assertions.assertNotNull(completeContent);
//    }
//@Test
//void testChatMemory() {
//    HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(1, "做个程序员鱼皮的工具网站，总代码量不超过 20 行");
//    Assertions.assertNotNull(result);
//    result = aiCodeGeneratorService.generateHtmlCode(1, "不要生成网站，告诉我你刚刚做了什么？");
//    Assertions.assertNotNull(result);
//    result = aiCodeGeneratorService.generateHtmlCode(2, "做个程序员鱼皮的工具网站，总代码量不超过 20 行");
//    Assertions.assertNotNull(result);
//    result = aiCodeGeneratorService.generateHtmlCode(2, "不要生成网站，告诉我你刚刚做了什么？");
//    Assertions.assertNotNull(result);
//}

}
