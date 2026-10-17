package com.wty.superaiagent.app;

import com.wty.superaiagent.advisor.MyReReadingAdvisor;
import com.wty.superaiagent.advisor.MySimpleLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 健身助手 AI 应用
 * <p>
 * 基于 Spring AI 的 ChatClient 封装，多轮对话ai应用
 * 通过 conversationId 支持多用户/多会话的记忆隔离。
 * <p>
 * 系统提示词不再硬编码在 Java 代码里，而是从
 * resources/templates/fitnessapp-system-prompt.st 模板文件加载，
 * 便于修改提示词时无需重新编译代码。
 *
 * @author 王天一
 * @version 1.0
 */
@Component
@Slf4j
public class FitnessApp {

    /**
     * 1
     * ChatClient 是 Spring AI 中与大模型交互的核心客户端，
     * 封装了 prompt 构建、advisor 拦截链、模型调用等能力
     */
    private final ChatClient chatClient;
    /**
     * 基础系统提示词：从 .st 模板文件读出的字符串
     * 供 doAskWithReport 在运行时覆盖 system 时手动拼接复用，
     * 避免提示词内容在代码里出现两份
     */
    private final String baseSystemPrompt;
    //原硬编码的系统提示词，已迁移到 resources/templates/fitnessapp-system-prompt.st
    //private static final String SYSTEM_PROMPT = "你是一个专业的健身教练，擅长回答用户的健身问题。";

    //构造器：Spring 自动注入三个依赖——
    //1. ChatModel：由 spring-ai-starter-model-openai 根据 application.yml 创建
    //2. ChatMemory：来自 ChatMemoryConfig 定义的 Bean（JDBC 持久化，重启不丢记忆）
    //3. templateResource：@Value 把 classpath 下的 .st 模板文件包装成 Resource 注入
    public FitnessApp(ChatModel chatModel,
                      ChatMemory chatMemory,
                      //注入PromptTemplate  从文件加载模板
                      @Value("templates/fitnessapp-system-prompt.st") Resource templateResource) throws IOException {
        //之前内存版写法：ChatMemory chatMemory = MessageWindowChatMemory.builder().build();
        //改为注入 ChatMemoryConfig 配置的 JDBC 持久化 Bean

        //在这里把文件内容读成字符串然后赋值（按 UTF-8 解码）
        this.baseSystemPrompt = templateResource.getContentAsString(StandardCharsets.UTF_8);

        this.chatClient = ChatClient
                .builder(chatModel)
                //系统 prompt：支持直接传 Resource，框架会自动读取模板文件内容作为系统提示词，每次请求都会带上
                .defaultSystem(templateResource)
                //对话记忆拦截器：请求前从 ChatMemory 加载历史消息拼入 prompt，响应后再把新消息存回 ChatMemory
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        //加入自定义的日志拦截器
                        new MySimpleLoggerAdvisor()
                        //自定义re2拦截器：暂时关闭（Re2 会把问题重复两遍，和长系统提示词叠加时 token 消耗较大）
                        //        new MyReReadingAdvisor()
                )
                .build();
    }

    /**
     * 2
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
                .user(message)                              //设置用户提示词
                //设置 advisor 运行时参数：指定本次请求的会话 ID，
                //MessageChatMemoryAdvisor 会读取该参数来决定加载/保存哪个会话的记忆
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()//同步调用大模型（阻塞直到返回）
                .chatResponse();                            //获取完整的响应对象（包含 token 用量等元信息）

        //从响应中提取 AI 回复的文本内容
        String result = chatResponse.getResult().getOutput().getText();
        log.info("回复内容：：：{}", result);//日志记录方便调试
        return result;
    }


    /**
     * 5结构化输出
     * 健身报告类，用于大模型的结构化输出
     * <p>
     * 定义为嵌套 record，字段名和类型就是期望 AI 返回的 JSON 结构：
     * Spring AI 会把它转成 JSON Schema 注入 prompt，要求模型按此格式回答；
     * 拿到响应后再自动反序列化成该对象，无需手动解析 JSON。
     * <p>
     * 注意：record 的访问器是 title()、suggestions()，不是 getTitle() getSuggestions()
     */
    record FitnessReport(String title, List<String> suggestions) {
    }

    /**
     * 对话方法（结构化输出版）：让 AI 以固定的结构返回健身报告
     * <p>
     * 与 {@link #doAsk} 的区别在于最后一环用 .entity(FitnessReport.class)
     * 替代 .chatResponse()，直接拿到反序列化好的 FitnessReport 对象
     *
     * @param message        用户输入的问题
     * @param conversationId 会话 ID，用于隔离不同会话的对话记忆
     * @return 结构化的健身报告对象
     */
    public FitnessReport doAskWithReport(String message, String conversationId) {
        FitnessReport fitnessReport = chatClient
                .prompt()
                //运行时 .system() 会整体覆盖 defaultSystem（不是追加），
                //所以这里必须手动拼上模板里的基础提示词 baseSystemPrompt，否则角色设定会丢失
                .system(baseSystemPrompt + "你需要提供一个结构化的健身报告，包含报告标题和建议列表")
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()                                     //同步调用大模型
                //.entity() 结构化输出：把模型的 JSON 响应自动转换为 FitnessReport 对象
                .entity(FitnessReport.class);

        //打印报告内容方便调试（record 自带 toString，会输出 title 和 suggestions）
        String result = fitnessReport.toString();
        log.info("回复内容：：：{}", result);
        return fitnessReport;
    }
}
