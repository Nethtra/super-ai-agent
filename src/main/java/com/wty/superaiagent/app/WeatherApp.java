package com.wty.superaiagent.app;

import com.wty.superaiagent.advisor.MySimpleLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * 天气助手 AI 应用
 * <p>
 * 基于 Spring AI 的 ChatClient 封装，多轮对话ai应用
 * 通过 conversationId 支持多用户/多会话的记忆隔离。
 *
 * @author 王天一
 * @version 1.0
 */
@Component
@Slf4j
public class WeatherApp {

    /**
     * ChatClient 是 Spring AI 中与大模型交互的核心客户端，
     * 封装了 prompt 构建、advisor 拦截链、模型调用等能力
     */
    private final ChatClient chatClient;

    //构造器：Spring 自动注入配置好的 ChatModel（由 spring-ai-starter-model-openai 根据 application.yml 创建）
    public WeatherApp(ChatModel chatModel) {
        //对话记忆：创建基于内存的窗口记忆，默认保留最近 20 条消息，超出后自动淘汰最早的消息
        ChatMemory chatMemory = MessageWindowChatMemory.builder().build();

        this.chatClient = ChatClient
                .builder(chatModel)
                .defaultSystem("你是一个天气助手")//系统 prompt：设定 AI 的角色，每次请求都会带上
                //对话记忆拦截器：请求前从 ChatMemory 加载历史消息拼入 prompt，响应后再把新消息存回 ChatMemory
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()
                        //加入自定义的日志拦截器
                        , new MySimpleLoggerAdvisor())
                .build();
    }

    /**
     * 对话方法
     *
     * @param message        用户输入的问题
     * @param conversationId 会话 ID，用于隔离不同会话的对话记忆；
     *                       相同 ID 的多次调用共享上下文，可实现多轮追问
     * @return AI 的回复文本
     */
    public String doAsk(String message, String conversationId) {
        // 发起一次完整的对话请求
        ChatResponse chatResponse = chatClient
                .prompt()                                   //开始构建一次请求
                .user(message)                              //设置用户消息
                //设置 advisor 运行时参数：指定本次请求的会话 ID，
                //MessageChatMemoryAdvisor 会读取该参数来决定加载/保存哪个会话的记忆
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()                                     //同步调用大模型（阻塞直到返回）
                .chatResponse();                            //获取完整的响应对象（包含 token 用量等元信息）

        //从响应中提取 AI 回复的文本内容
        String result = chatResponse.getResult().getOutput().getText();
        log.info("info{}", result);//日志记录方便调试
        return result;
    }
}
