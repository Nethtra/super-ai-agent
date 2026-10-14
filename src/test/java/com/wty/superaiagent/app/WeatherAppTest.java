package com.wty.superaiagent.app;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试调用weathapp
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
class WeatherAppTest {

    @Resource
    private WeatherApp weatherApp;

    @Test
    void testDoAsk() {

        String coversationId = UUID.randomUUID().toString();
        //第一轮对话
        String answer = weatherApp.doAsk("你好，我在北京", coversationId);
        Assertions.assertNotNull(answer);
        //第二轮对话
        answer = weatherApp.doAsk("你好，我现在在广州", coversationId);
        Assertions.assertNotNull(answer);
        //第三轮对话
        answer = weatherApp.doAsk("你好，我在哪里来着？", coversationId);
        Assertions.assertNotNull(answer);
    }
}