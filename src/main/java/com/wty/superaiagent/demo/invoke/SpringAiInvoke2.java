package com.wty.superaiagent.demo.invoke;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * @author 王天一
 * @version 1.0
 * springai另一种方式测试
 */
@Component
public class SpringAiInvoke2 implements CommandLineRunner {//实现CommandLineRunner接口的类 启动springboot时会自动执行run方法
    @Resource
    private ChatModel openaiChatModel;

    @Override
    public void run(String... args) throws Exception {
//        AssistantMessage assistantMessage = openaiChatModel.call(new Prompt("你好，你是谁")).getResult().getOutput();
//        System.out.println(assistantMessage.getText());
    }
}
