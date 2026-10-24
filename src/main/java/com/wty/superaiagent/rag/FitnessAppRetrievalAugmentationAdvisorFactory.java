package com.wty.superaiagent.rag;

import org.jspecify.annotations.NonNull;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

/**
 * 16 17 自定义 RetrievalAugmentationAdvisor：用 DocumentRetriever 加自定义过滤条件。
 * <p>
 * 一、这个类在做什么
 * 它不参与检索也不参与生成，只负责"装配"：把 VectorStore、过滤条件、相似度阈值、topK 组装成一个
 * {@link RetrievalAugmentationAdvisor}。这个 Advisor 挂到 ChatClient 上之后，
 * 每次对话会自动完成"检索 → 拼上下文 → 交给模型"。
 * <p>
 * 名词说明：
 * <ul>
 *     <li><b>Advisor</b>：ChatClient 的拦截器，在请求发给模型之前/之后插入一段逻辑。
 *     RAG Advisor 就是在"发请求之前"去向量库检索，并把结果拼进提示词。</li>
 *     <li><b>DocumentRetriever</b>：文档检索器，职责是"给定一个查询，返回若干 Document"。
 *     本类用的是它的向量库实现，所以需要 VectorStore。</li>
 *     <li><b>Filter.Expression</b>：元数据过滤条件，形如 {@code purpose == "减脂"}。
 *     它不是对文本内容做过滤，而是对 Document 的 metadata（键值对）做过滤，
 *     最终会由 VectorStore 翻译成数据库查询条件（pgvector 下是 SQL 里的 JSON 条件）。</li>
 * </ul>
 * <p>
 * 二、整条 RAG 流程中本类影响哪一环
 * <pre>
 *   QueryTransformer（可选，改写/扩展查询） → 【DocumentRetriever：过滤 + 相似度检索 + topK】 → DocumentJoiner（合并） → QueryAugmenter（拼进提示词） → ChatModel
 * </pre>
 * 本类只定制了中间那一环的检索参数。
 * <p>
 * 三、为什么写成"工厂"而不是 @Component 组件
 * 这个类没有成员变量、不保存状态，唯一作用是 new 出一个 Advisor。
 * 通常在一个配置类里按不同的 purpose 分别调用 create 方法，
 * 得到多个过滤条件不同的 Advisor（例如减脂一个、增肌一个）。
 * 它本身不需要被容器管理，所以不贴 @Component 注解。
 * <p>
 *
 * @author 王天一
 * @version 1.0
 */
public class FitnessAppRetrievalAugmentationAdvisorFactory {


    public static RetrievalAugmentationAdvisor create(VectorStore vectorStore, String purpose) {

        //根据metadata过滤指定的文档
        Filter.Expression expression = new FilterExpressionBuilder()
                .eq("purpose", purpose)
                .build();

        // 构建检索器：向量库 + 过滤条件 + 相似度阈值 + 返回条数
        DocumentRetriever vectorStoreDocumentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .filterExpression(expression)//过滤条件
                .similarityThreshold(0.4)//相似度阈值
                .topK(3)//返回最相似的Document数量
                .build();

        // 组装 Advisor
        return RetrievalAugmentationAdvisor.builder()
                .documentRetriever(vectorStoreDocumentRetriever)
                .queryAugmenter(FitnessAppContextualQueryAugmenterFactory.create())//17应用自定义的上下文增强器
                .build();
    }
}
