package com.wty.superaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 9 DocumentWriter
 * 配置SimpleVectorStore,将文档存入向量存储中
 *
 * @author 王天一
 * @version 1.0
 */
@Configuration
public class FitnessAppVectorStoreConfig {
    @Resource
    private MyMarkdownDocumentReader myMarkdownDocumentReader;

    @Bean//指定注入的Bean为openAiEmbeddingModel
    public VectorStore fitnessAppVectorStore(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        List<Document> documents = myMarkdownDocumentReader.readMarkdownDocuments();
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        simpleVectorStore.doAdd(documents);
        return simpleVectorStore;
    }
}
