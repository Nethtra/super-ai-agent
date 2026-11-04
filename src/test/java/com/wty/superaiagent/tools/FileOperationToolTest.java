package com.wty.superaiagent.tools;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 18测试 文件读写工具
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
class FileOperationToolTest {

    @Test
    void readFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String content = fileOperationTool.readFile("hello.txt");
        Assertions.assertNotNull(content);
        log.info("content:{}", content);
    }

    @Test
    void writeFile() {
        FileOperationTool fileOperationTool = new FileOperationTool();
        String content = fileOperationTool.writeFile("hello.txt", "你好世界");
        Assertions.assertNotNull(content);
    }
}