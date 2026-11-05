package com.wty.superaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 王天一
 * @version 1.0
 */
@Slf4j
class ResourceDownloadToolTest {

    @Test
    void downloadFile() {
        ResourceDownloadTool resourceDownloadTool = new ResourceDownloadTool();
        String result = resourceDownloadTool.downloadFile("https://docs.spring.io/spring-ai/reference/_images/spring-ai-concepts-model-types.jpg", "spring.png");
        log.info(result);
    }
}