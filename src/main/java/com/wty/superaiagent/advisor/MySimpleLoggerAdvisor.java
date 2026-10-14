package com.wty.superaiagent.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientMessageAggregator;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import reactor.core.publisher.Flux;

/**
 * 自定义日志拦截器（Advisor）
 * <p>
 * 模仿 Spring AI 内置的 SimpleLoggerAdvisor 实现的自定义拦截器，
 * 同时实现了两个接口以覆盖两种调用方式：
 * <ul>
 *     <li>{@link CallAdvisor}：同步调用（chatClient.prompt()...call()）时生效</li>
 *     <li>{@link StreamAdvisor}：流式调用（chatClient.prompt()...stream()）时生效</li>
 * </ul>
 * <p>
 * 拦截链机制：advisor 之间像责任链一样串联，
 * 调用 nextCall()/nextStream() 会把请求交给链上的下一个 advisor（最终到达大模型），
 * 之后的代码则在响应返回时执行，从而可以在前后分别记录日志。
 * <p>
 * 使用方式：注册到 ChatClient 中，例如
 * .defaultAdvisors(new MySimpleLoggerAdvisor())
 *
 * @author 王天一
 * @version 1.0
 */
@Slf4j
public class MySimpleLoggerAdvisor implements CallAdvisor, StreamAdvisor {

    /**
     * advisor 的名称，用于日志展示和调试时区分链上的不同 advisor
     */
    @Override
    public String getName() {
        return "自定义日志拦截器";
    }

    /**
     * advisor 在拦截链中的执行顺序，数值越小越先执行
     * <p>
     * 日志 advisor 一般希望包裹在最外层，以便记录经过其他 advisor
     * （如记忆拦截器）处理后的完整请求
     */
    @Override
    public int getOrder() {
        return 0;
    }


    /**
     * 同步调用的拦截逻辑：请求前打印日志 → 放行 → 响应后打印日志
     *
     * @param chatClientRequest 封装了 prompt、上下文参数的请求对象
     * @param callAdvisorChain  同步调用拦截链，用于把请求传递给下一个 advisor
     * @return 大模型（或下游 advisor 处理后）的响应
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        logRequest(chatClientRequest);

        //把请求交给拦截链的下一个节点继续处理，拿到最终响应
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);

        logResponse(chatClientResponse);

        return chatClientResponse;
    }

    /**
     * 流式调用的拦截逻辑：响应以 Flux 分片异步返回，不能直接打印
     * <p>
     * 借助 ChatClientMessageAggregator 把流中的多个响应分片聚合成完整响应，
     * 在流完成时触发 logResponse 打印聚合后的完整日志，
     * 同时不影响流式内容实时返回给调用方
     *
     * @param chatClientRequest 封装了 prompt、上下文参数的请求对象
     * @param streamAdvisorChain 流式调用拦截链，用于把请求传递给下一个 advisor
     * @return 聚合处理后的响应流
     */
    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest chatClientRequest,
                                                 StreamAdvisorChain streamAdvisorChain) {
        logRequest(chatClientRequest);

        //把请求交给拦截链的下一个节点，得到分片的响应流
        Flux<ChatClientResponse> chatClientResponses = streamAdvisorChain.nextStream(chatClientRequest);

        //聚合分片并在流完成时打印完整响应日志（this::logResponse 为方法引用）
        return new ChatClientMessageAggregator().aggregateChatClientResponse(chatClientResponses, this::logResponse);
    }

    /**
     * 打印请求日志
     */
    private void logRequest(ChatClientRequest request) {
        log.info("request: {}", request);
    }

    /**
     * 打印响应日志
     */
    private void logResponse(ChatClientResponse chatClientResponse) {
        log.info("response: {}", chatClientResponse);
    }

}
