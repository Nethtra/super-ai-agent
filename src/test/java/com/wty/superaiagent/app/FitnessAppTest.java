package com.wty.superaiagent.app;

import cn.hutool.core.lang.UUID;
import com.wty.superaiagent.tools.PDFGenerationTool;
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
    void doAsk() {
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
    void doAskWithReport() {
        String conversationId = UUID.randomUUID().toString();
        FitnessApp.FitnessReport fitnessReport = fitnessApp.doAskWithReport("你好，我正在练腰部肌肉，但是我遇到了瓶颈", conversationId);
        Assertions.assertNotNull(fitnessReport);
    }

    @Test
    void doAskWithRAG() {
        String conversationId = UUID.randomUUID().toString();
        //提问文档内的问题
        String answer = fitnessApp.doAskWithRAG("你好，我正在减脂，我已经做过不少有氧运动了", conversationId);
        Assertions.assertNotNull(answer);
    }


    @Test
    void doAskWithRAGAndRetrievalAugmentationAdvisor() {
        String conversationId = UUID.randomUUID().toString();
        //提问文档内的问题
        String answer = fitnessApp.doAskWithRAG("你好，我正在减脂，我已经做过不少有氧运动了", conversationId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doAskWithToolCalling() {
        testToolCalling("我70kg 180cm，帮我计算我的BMI，基础代谢率 BMR 与每日总消耗 TDEE 每日蛋白/脂肪/碳水克数");
        testToolCalling("读一下hello.txt");
        testToolCalling("我180cm，120kg，帮我指定一个健身计划200字，写入pdf");
        testToolCalling("帮我下载一张肌肉的图片");
        testToolCalling("看一下我电脑上的java的版本");
        testToolCalling("上网搜索一下主流的健身和健康网站");
    }

    private void testToolCalling(String message) {
        String conversationId = UUID.randomUUID().toString();
        String answer = fitnessApp.doAskWithToolCalling(message, conversationId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doAskWithMCP() {
        String conversationId = UUID.randomUUID().toString();
        String answer = fitnessApp.doAskWithMCP("帮我推荐几个广州市天河区的健身房，我想健身", conversationId);
        Assertions.assertNotNull(answer);
    }

//    @Test
//    void doAskWithCloudRAG() {
//        String conversationId = UUID.randomUUID().toString();
//        //提问文档内的问题
//        String answer = fitnessApp.doAskWithRAG("你好，我正在减脂，我做了不少有氧运动了", conversationId);
//        Assertions.assertNotNull(answer);
//    }
}