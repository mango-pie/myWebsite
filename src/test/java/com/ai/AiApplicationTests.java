package com.ai;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// 需要完整 Spring 上下文（DB/Redis 等），归类为 integration，默认在 CI/本地 mvn verify 中跳过。
// 需要时用 mvn -Dgroups=integration test 单独运行。
@Tag("integration")
@SpringBootTest
class AiApplicationTests {

    @Test
    void contextLoads() {

    }

}
