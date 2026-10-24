package com.wty.superaiagent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.List;

/**12
 * 自定义基于 Token 的文本切割器（属于 DocumentTransformer）
 * <p>
 * 为什么用 {@code builder()} 而不是 {@code new TokenTextSplitter(...)}：
 * Spring AI 2.0.0 起，TokenTextSplitter 的全部构造器都被标记为 {@link Deprecated}，
 * 并且原来的 5 参构造器 {@code (int chunkSize, int minChunkSizeChars, int minChunkLengthToEmbed,
 * int maxNumChunks, boolean keepSeparator)} 已被直接删除，
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class MyTokenTextSplitter {

    /**
     * 使用全部默认参数切割文档。
     *
     * @param documents 待切割的文档（通常来自 DocumentReader）
     * @return 切割后的文档分块
     */
    public List<Document> splitDocuments(List<Document> documents) {
        // 2.0 中无参构造器虽仍可用但已过时，统一走 builder 保持写法一致
        TokenTextSplitter splitter = TokenTextSplitter.builder().build();
        // apply() 来自 Function<List<Document>, List<Document>>，语义等价于 transform()
        return splitter.apply(documents);
    }

    /**
     * 使用自定义参数切割文档（更小的块，适合短知识库条目/QA 场景）。
     *
     * @param documents 待切割的文档
     * @return 切割后的文档分块
     */
    public List<Document> splitCustomized(List<Document> documents) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(200)             // 每块最多 200 token
                .withMinChunkSizeChars(100)     // 每块至少 100 字符
                .withMinChunkLengthToEmbed(10)  // 小于 10 字符的碎块丢弃
                .withMaxNumChunks(5000)         // 单次最多 5000 块
                .withKeepSeparator(true)        // 保留分隔符/标点
                .build();
        return splitter.apply(documents);
    }
}
