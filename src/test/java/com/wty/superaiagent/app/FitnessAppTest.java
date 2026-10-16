package com.wty.superaiagent.app;

import cn.hutool.core.lang.UUID;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 测试调用weatherapp
 *
 * @author 王天一
 * @version 1.0
 */
@SpringBootTest
class FitnessAppTest {

    @Resource
    private FitnessApp fitnessApp;

    @Test
    void testDoAsk() {

        String conversationId = UUID.randomUUID().toString();
        //第一轮对话
        String answer = fitnessApp.doAsk("你好，我在北京", conversationId);
        Assertions.assertNotNull(answer);
        //第二轮对话
        answer = fitnessApp.doAsk("你好，我现在在广州", conversationId);
        Assertions.assertNotNull(answer);
        //第三轮对话
        answer = fitnessApp.doAsk("你好，我在哪里来着？", conversationId);
        Assertions.assertNotNull(answer);
    }


    @Test
    void TestDoAskWithReport() {
        String conversationId = UUID.randomUUID().toString();
        FitnessApp.FitnessReport fitnessReport = fitnessApp.doAskWithReport("你好，我正在练腰部肌肉，但是我遇到了瓶颈", conversationId);
        Assertions.assertNotNull(fitnessReport);
    }
}