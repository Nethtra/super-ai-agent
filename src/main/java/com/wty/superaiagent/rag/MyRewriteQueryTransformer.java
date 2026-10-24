package com.wty.superaiagent.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 15 自定义重写查询转换器（QueryTransformer）。
 * <p>
 * 一、它做什么
 * 把用户原始提问改写成"更适合做向量检索"的表述，再拿改写结果去检索。
 *   原始 Query ──LLM 改写──▶ 一个更"检索友好"的 Query ──向量检索──▶ 拼接上下文 ──▶ 生成答案
 * 二、在 RAG 流程中的位置
 * 与 {@link MyMultiQueryExpander} 同属 <b>pre-retrieval（检索前）</b>阶段的 QueryTransformer，
 * 区别是：多查询扩展是"一变多"（提高召回），这里是"一对一改写"（提高精度、减少无关召回）。
 * 二者一般不同时使用；要串起来时注意顺序——先改写（收敛表述），再扩展（发散召回）。
 * <p>
 * 三、本类的两个实现选择
 * <ol>
 *     <li>转换器在<b>构造阶段</b>创建一次并复用。{@link RewriteQueryTransformer} 内部是无状态的
 *     （线程安全），而改写用的 ChatClient 构建有开销，没必要每次调用都 new；</li>
 *     <li>字段类型声明成接口 {@link QueryTransformer}，而不是具体类，
 *     后续想换成 CompressionQueryTransformer / TranslationQueryTransformer 时只需改构造器那一行。</li>
 * </ol>
 * <p>
 * 四、使用注意（踩坑点）
 * <ul>
 *     <li>多一次 LLM 调用：每次检索前都要额外请求一次模型，延迟和费用都会上升；</li>
 *     <li>改写过猛会"跑题"：提示词里越强调改写自由度，越可能偏离原意，
 *     真实项目里一般通过 {@code RewriteQueryTransformer.builder().targetSearchEngine(...)}
 *     告诉模型目标检索系统（如 "vector store"）来约束输出风格；</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class MyRewriteQueryTransformer {


    private final QueryTransformer queryTransformer;

    public MyRewriteQueryTransformer(ChatModel chatModel) {
        // 手写 ChatClient.Builder：与直接注入自动装配的 ChatClient.Builder 相比，
        // 这里不会带上自动配置里的默认 advisors / observation 定制，
        // 好处是"干净"、只做改写；若希望把重写过程也纳入链路追踪，改成注入 ChatClient.Builder 更方便
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        // 创建查询重写转换器（默认使用内置的改写提示词模板；可再加 .targetSearchEngine(...) 约束输出）
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();
    }

    /**
     * 对一句查询做重写，返回改写后的文本。
     * <p>该方法主要方便手动验证改写效果（比如在测试里直接调用看输出），
     * 真正接入 RAG 时可把内部的 {@link #queryTransformer} 直接交给
     * RetrievalAugmentationAdvisor 的 preRetrievalTransformers 使用。
     *
     * @param prompt 用户的原始提问
     * @return 改写后的查询文本
     */
    public String doQueryRewrite(String prompt) {
        // Query 是 RAG 模块的查询载体，除了 text 还带 history/context（这里只用文本）
        Query query = new Query(prompt);
        // 执行查询重写：内部拼提示词 → 调用 ChatModel → 把响应文本包成新的 Query
        // transform() 是 QueryTransformer 的唯一抽象方法，语义上等价于 apply()
        Query transformedQuery = queryTransformer.transform(query);
        // 只取文本：注意这里丢掉了 history 等上下文，仅用于观察改写结果
        return transformedQuery.text();
    }
}
