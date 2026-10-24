package com.wty.superaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**9
 * 本地向量知识库配置（RAG 的存储与检索底座）
 * <p>
 * 职责：把 {@link MyMarkdownDocumentReader} 从 Markdown 文件切出的文档，
 * 交给 EmbeddingModel 向量化后，存入 SimpleVectorStore 供 RAG 检索使用。
 * <p>
 * 容器启动时即完成"读取 → 向量化 → 入库"，之后 FitnessApp#doAskWithRAG
 * 通过 QuestionAnswerAdvisor 到这个库里做相似度检索。
 * <p>
 * SimpleVectorStore 特点：向量存在本机内存（可持久化到 JSON 文件），
 * 零外部依赖，适合学习和小规模知识库；生产环境数据量大时
 * 通常换 pgvector、Milvus 等专用向量数据库（接口同为 VectorStore，可无缝替换）。
 * <p>
 * 注意：每次应用重启都会重新读取并向量化全部文档（消耗 embedding 调用），
 * 文档量大时可考虑持久化或增量加载
 *
 * @author 王天一
 * @version 1.0
 */
//11只有显式在yml配置 simple-vector-store.type=simple 时这个类才生效；不配置就默认关闭
//由于@Bean的优先级高于自动配置，所以关闭后SimpleVectorStore就不会被注册到容器中，让位于pgVectorStore的自动装配
@ConditionalOnProperty(name = "simple-vector-sotore.type", havingValue = "simple", matchIfMissing = false)
@Configuration
public class FitnessAppVectorStoreConfig {
    /**
     * Markdown 文档读取器：负责从 classpath:documents/*.md 读取并切分文档
     * （用 @Resource 按名称注入本包内的自定义组件）
     */
    @Resource
    private MyMarkdownDocumentReader myMarkdownDocumentReader;

    /**
     * 构建本地向量知识库
     * <p>
     * 用 @Qualifier 指定注入 openAiEmbeddingModel
     * 因为容器中曾同时存在 dashscope 和 openai 两个 EmbeddingModel Bean，
     * 按类型注入会有歧义，必须显式指定用哪一个
     *
     * @param embeddingModel 向量化模型，把文本转成高维向量（相似度检索的依据）
     * @return 已完成文档入库的向量知识库
     */
    //因为@Bean/@Component 先注册到容器，自动配置最后处理，所以pgvectorstore的starter看到容器里有VectorStore的bean，就不会注入
    //所以如果要切换到pgvectorstore实现的话，要么删除这个类，要么使用@ConditionalOnProperty控制这个类不生效
    @Bean
    public VectorStore fitnessAppVectorStore(@Qualifier("openAiEmbeddingModel") EmbeddingModel embeddingModel) {
        //第一步：读取 documents 目录下所有 Markdown，切分成 Document 列表
        List<Document> documents = myMarkdownDocumentReader.readMarkdownDocuments();
        //第二步：创建基于内存的向量存储，绑定向量化模型
        SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
        //第三步：把文档批量入库——内部自动调用 embeddingModel 把每条文本向量化后存储
        simpleVectorStore.doAdd(documents);
        return simpleVectorStore;
    }
}
