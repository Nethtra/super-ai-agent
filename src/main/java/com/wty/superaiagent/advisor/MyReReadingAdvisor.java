package com.wty.superaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.prompt.PromptTemplate;

import java.util.Map;

/**4
 * 自定义 Re2 拦截器：重读 prompt，提高输出准确性  实现Spring2.0 BaseAdvisor
 * <p>
 * 实现了 {@link BaseAdvisor} 接口，它把拦截逻辑拆成了两个更直观的钩子：
 * <ul>
 *     <li>{@link #before}：请求发给大模型之前，用于改写/增强请求（本类的核心逻辑在这里）</li>
 *     <li>{@link #after}：拿到响应之后，用于处理响应（本类不需要处理，原样返回）</li>
 * </ul>
 * 相比直接实现 CallAdvisor/StreamAdvisor，BaseAdvisor 由框架自动处理
 * 同步和流式两条调用路径，代码更简洁。
 * <p>
 * 使用方式：注册到 ChatClient 中，例如
 * .defaultAdvisors(new MyReReadingAdvisor())
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
public class MyReReadingAdvisor implements BaseAdvisor {

    /**
     * 默认的重读 prompt 模板：
     * 第一行是原始问题，第二行把同一个问题再重复一遍并提示"重新读题"
     * {re2_input_query} 是模板占位符，渲染时会被替换为用户实际输入
     */
    private static final String DEFAULT_RE2_ADVISE_TEMPLATE = """
            {re2_input_query}
            Read the question again: {re2_input_query}
            """;

    /**
     * 实际使用的重读模板，支持通过构造器自定义
     */
    private final String re2AdviseTemplate;

    /**
     * advisor 在拦截链中的执行顺序，数值越小越先执行，默认为 0
     */
    private int order = 0;

    /**
     * 使用默认重读模板创建
     */
    public MyReReadingAdvisor() {
        this(DEFAULT_RE2_ADVISE_TEMPLATE);
    }

    /**
     * 使用自定义重读模板创建
     *
     * @param re2AdviseTemplate 自定义模板，需包含 {re2_input_query} 占位符
     */
    public MyReReadingAdvisor(String re2AdviseTemplate) {
        this.re2AdviseTemplate = re2AdviseTemplate;
    }

    /**
     * 请求前拦截：把用户输入套进重读模板，生成"问题读两遍"的新 prompt
     * <p>
     *
     * @param chatClientRequest 原始请求
     * @param advisorChain      拦截链
     * @return 改写后的新请求对象
     */
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        //用 PromptTemplate 渲染模板：把 {re2_input_query} 占位符替换为用户的实际输入
        String augmentedUserText = PromptTemplate.builder()
                .template(this.re2AdviseTemplate)
                .variables(Map.of("re2_input_query", chatClientRequest.prompt().getUserMessage().getText()))
                .build()
                .render();

        //请求对象不可变，通过 mutate() 创建一个替换了 user 消息的新请求
        return chatClientRequest.mutate()
                .prompt(chatClientRequest.prompt().augmentUserMessage(augmentedUserText))
                .build();
    }

    /**
     * 响应后拦截：Re2 技术只改写请求，响应无需处理，原样返回
     */
    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    /**
     * 返回本 advisor 在拦截链中的执行顺序
     */
    @Override
    public int getOrder() {
        return this.order;
    }

    /**
     * 设置执行顺序（链式调用的流式写法）
     *
     * @param order 顺序，数值越小越先执行
     * @return 当前实例，支持链式调用
     */
    public MyReReadingAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }

}
