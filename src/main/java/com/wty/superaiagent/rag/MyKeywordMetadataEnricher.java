package com.wty.superaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 13 自定义关键词增强器（属于 DocumentTransformer），给每个 Document 补充"关键词"元数据。
 * <p>
 * 一、它在 ETL 里的位置
 *   DocumentReader（读 Markdown） → TextSplitter（切块） → 【MetadataEnricher（补元数据）】 → VectorStore.add（向量化入库）
 * 增强器不改变文档正文，只往 {@code Document.metadata} 里塞键值对；这些元数据会跟着文档一起入库，
 * 后续检索时可以按元数据过滤（filter expression），或把关键词拼进上下文帮助模型理解主题。
 * 三、实现机制
 * {@link KeywordMetadataEnricher} 内部持有一个提示词模板，对<b>每一篇</b>文档发一次对话模型请求，
 * 让模型总结出指定数量的关键词，然后写入 metadata 的 {@code excerpt_keywords} 键（逗号分隔的字符串）。
 * 由于使用LLM，所以考虑耗时和成本问题
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class MyKeywordMetadataEnricher {

    /**
     * 对话模型，用于让 LLM 阅读文档并提炼关键词（由 Spring AI 自动装配的 ChatModel 实现注入）
     */
    @Resource
    private ChatModel chatModel;

    /**
     * 给一批文档补充关键词元数据。
     *
     * @param documents 待增强的文档列表（通常是 DocumentReader 读取或 TextSplitter 切割后的结果）
     * @return 增强后的文档列表：正文不变，每篇文档的 metadata 多出 {@code excerpt_keywords} 键
     */
    public List<Document> enrichDocumentsWithKeywords(List<Document> documents) {
        // 第二个参数 2 表示"每篇文档生成 2 个关键词"（keywordsCount），调大能让模型提炼更多词，但输出更长、更慢
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(chatModel, 2);
        // apply() 来自 DocumentTransformer（顶层是 Function<List<Document>, List<Document>>），
        // 语义等价于 2.x 新增的 transform()，内部会遍历每篇文档逐个调用模型
        return keywordMetadataEnricher.apply(documents);
    }
}
