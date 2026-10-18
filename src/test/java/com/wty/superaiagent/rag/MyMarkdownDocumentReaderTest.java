package com.wty.superaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**8
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
class MyMarkdownDocumentReaderTest {
    @Resource
    private MyMarkdownDocumentReader myMarkdownDocumentReader;

    @Test
    void testMyMarkdownDocumentReader() {
        myMarkdownDocumentReader.readMarkdownDocuments();
    }
}