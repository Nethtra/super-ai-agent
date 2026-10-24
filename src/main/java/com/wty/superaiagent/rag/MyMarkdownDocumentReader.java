package com.wty.superaiagent.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 8
 * 自定义DocumentReader MarkdownDocumentReader文档读取器（RAG 流程的第一步：DocumentLoader 将文档抽取到内存中）
 * <p>
 * 负责把 classpath:documents/ 目录下的所有 Markdown 文件读入内存，
 * 并切割成一个个 {@link Document}（RAG 中后续向量化/入库的基本单位）。。
 * <p>
 * 典型用途：把切好的 documents 喂给 TokenTextSplitter 进一步分块，
 * 再写入 VectorStore（如 pgvector/milvus）供 RAG 检索
 *
 * @author 王天一
 * @version 1.0
 */
@Component
@Slf4j
public class MyMarkdownDocumentReader {
    /**
     * Spring 的资源解析器，支持 classpath:*.md 这类通配符写法批量定位资源文件
     */
    private final ResourcePatternResolver resourcePatternResolver;

    public MyMarkdownDocumentReader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }

    /**
     * 读取 documents 目录下全部 Markdown 文件，切割并返回 Document 列表
     *
     * @return 所有文件切分出的 Document 集合；读取失败时返回已成功读取的部分（不抛异常）
     */
    public List<Document> readMarkdownDocuments() {
        List<Document> documents = new ArrayList<>();
        try {
            //读取documents目录下的所有markdown
            Resource[] resources = resourcePatternResolver.getResources("classpath:documents/*.md");

            for (Resource resource : resources) {//每个文件
                String filename = resource.getFilename();
                String purpose = filename.substring(0, 2);//切割前两个字
                //MarkdownDocumentReaderConfig 决定如何切割文档：
                MarkdownDocumentReaderConfig markdownDocumentReaderConfig = MarkdownDocumentReaderConfig.builder()
                        //遇到分割线（---）就切出一个新的 Document
                        .withHorizontalRuleCreateDocument(true)
                        //代码块不纳入切割结果（健身知识库里没有代码，直接丢弃）
                        .withIncludeCodeBlock(false)
                        //引用块（>）不纳入切割结果
                        .withIncludeBlockquote(false)
                        //每个切出来的 Document 都可以添加元数据标签，后续 RAG 检索命中时可通过 metadata 溯源到来源文件
                        .withAdditionalMetadata("filename", filename)//文件名
                        .withAdditionalMetadata("purpose", purpose)//健身的目的
                        .build();
                MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, markdownDocumentReaderConfig);
                //markdownDocumentReader.get()得到按上述规则切割后的List<Document>
                documents.addAll(markdownDocumentReader.get());
            }

        } catch (IOException e) {
            log.error("markdown读取失败", e);
        }

        return documents;

    }
}
