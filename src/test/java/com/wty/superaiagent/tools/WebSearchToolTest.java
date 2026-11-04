package com.wty.superaiagent.tools;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
@Slf4j
class WebSearchToolTest {

    @Resource
    private WebSearchTool webSearchTool;

    @Test
    void searchWeb() {
        String answer = webSearchTool.searchWeb("如何在三天内快速减肥");
        Assertions.assertNotNull(answer);
        log.info("answer:{}", answer);
    }
}