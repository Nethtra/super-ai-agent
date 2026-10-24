package com.wty.superaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 14 文档过滤与检索：自定义多查询扩展器（Multi-Query Expansion）。
 * <p>
 * 一、它解决什么问题
 * 用户的原始提问往往又短又口语（例如"我练腰遇到瓶颈了"），单一表述的向量相似度命中率有限。
 * 多查询扩展的做法是：让 LLM 把这一句改写成若干个语义相近、侧重点不同的查询，
 * 这样能扩大召回面（提高 recall），代价是检索次数和一次额外的 LLM 调用。
 * <pre>
 *   原始 Query ──LLM 改写──▶ Query1 / Query2 / ... ──分别检索──▶ 合并去重 ──▶ 交给模型生成答案
 * </pre>
 * <p>
 * 二、它在 RAG 流程中的位置
 * 属于 <b>pre-retrieval（检索前）</b>阶段的 QueryTransformer，通常挂到
 * RetrievalAugmentationAdvisor 的 {@code preRetrievalTransformers} 上；
 * 注意它转换的是"查询"，不是"文档"，和 {@code TextSplitter}、{@code MetadataEnricher}
 * 这些处理文档的 DocumentTransformer 完全不是一类东西。
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class MyMultiQueryExpander {

    /**
     * ChatClient 建造器（Spring AI 自动装配的原型 Bean），
     * MultiQueryExpander 内部会用它构造调用模型改写的 ChatClient
     */
    private final ChatClient.Builder chatClientBuilder;

    /**
     * 构造器注入：Spring 从容器里取出 ChatClient.Builder 传入，
     * 这样单测时可以自己 new 一个 Builder 或 Mock 来验证改写逻辑
     *
     * @param chatClientBuilder 对话客户端建造器
     */
    public MyMultiQueryExpander(ChatClient.Builder chatClientBuilder) {
        this.chatClientBuilder = chatClientBuilder;
    }

    /**
     * 把单个查询扩展成多个语义相近的查询。
     *
     * @param query 用户的原始查询（含文本、会话历史等上下文）
     * @return 扩展后的查询列表：通常是原始查询 + N 个改写版
     */
    public List<Query> expand(Query query) {
        // MultiQueryExpander 本身也是工具对象而非 Bean，每次调用 new 一个即可（构造开销很小）
        MultiQueryExpander multiQueryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(2)   // 额外生成 2 条改写查询；加上默认保留的原始查询，最终约 3 条
                .build();
        // 内部逻辑：拼提示词 → 调 LLM → 解析成多行文本 → 每行包成一个 Query 返回
        return multiQueryExpander.expand(query);
    }
}
