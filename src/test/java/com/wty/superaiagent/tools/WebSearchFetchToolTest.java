package com.wty.superaiagent.tools;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
@Slf4j
class WebSearchFetchToolTest {

    @Resource
    private WebSearchFetchTool webSearchFetchTool;

    @Test
    void searchWeb() {
        String answer = webSearchFetchTool.searchWeb("如何在三天内快速减肥");
        Assertions.assertNotNull(answer);
        log.info("answer:{}", answer);
    }

    @Test
    void fetchWebContent() {
        String content = webSearchFetchTool.fetchWebContent("https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html");
        Assertions.assertNotNull(content);
        log.info("content:{}", content);
    }
}