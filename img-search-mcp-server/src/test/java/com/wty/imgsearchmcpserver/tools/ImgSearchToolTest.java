package com.wty.imgsearchmcpserver.tools;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
class ImgSearchToolTest {

    @Resource
    private ImgSearchTool imgSearchTool;

    @Test
    void searchPhotos() {
        String result = imgSearchTool.searchPhotos("sports");
        Assertions.assertNotNull(result);
    }
}