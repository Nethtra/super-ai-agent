package com.wty.superaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 11 这个类实际上就是ETL的过程
 * 向量库文档初始化器：应用启动时把 Markdown 文档存入 pgvector
 * <p>
 * 背景：原先 SimpleVectorStore 的灌库逻辑写在 FitnessAppVectorStoreConfig 的 @Bean 里，
 * 且内存版每次重启都清空重灌；切换到 pgvector 后数据是持久化的，
 * 如果沿用"每次启动就 add"的写法，重启几次表里就有几份重复向量，
 * 因此拆出这个独立的 {@link ApplicationRunner}：启动尾声先查表判断是否已灌过，
 * <p>
 * 此时容器中 VectorStore 的具体实现就是自动装配的 PgVectorStore
 * （本配置随 pgvector starter 生效， FitnessAppVectorStoreConfig 已由开关关闭）
 * <p>
 * 使用注意：
 * <ul>
 *     <li>修改 Markdown 文档后想重新灌库：手动清空 vector_store 表再重启即可</li>
 *     <li>建表依赖 pgvector 自动配置的 initialize-schema=true，须在 ApplicationRunner 之前完成
 *     （Bean 创建阶段先于 Runner 执行，时序天然保证）</li>
 * </ul>
 *
 * @author 王天一
 * @version 1.0
 */
@Component
public class VectorStoreInitializer implements ApplicationRunner {

    /**
     * 向量知识库（自动装配的 PgVectorStore），负责向量化并入库
     */
    private final VectorStore vectorStore;
    /**
     * Markdown 文档读取器：从 classpath:documents/*.md 读取并切分文档
     */
    private final MyMarkdownDocumentReader myMarkdownDocumentReader;
    /**
     * JDBC 模板：用于查询 vector_store 表的行数，判断是否已灌过库
     */
    private final JdbcTemplate jdbcTemplate;

    //12T 文本切割器 但是不建议使用基于Token的 因为切得太暴力了
    @Resource
    public MyTokenTextSplitter myTokenTextSplitter;

    //13T 元数据增强器
    @Resource
    public MyKeywordMetadataEnricher myKeywordMetadataEnricher;

    //构造器注入三个依赖，与 @Resource 字段注入等效，且便于单测时传入 Mock
    public VectorStoreInitializer(VectorStore vectorStore,
                                  MyMarkdownDocumentReader reader,
                                  JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.myMarkdownDocumentReader = reader;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        //查询当前向量库中的文档数，作为"是否已灌过库"的判断依据
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM vector_store", Integer.class);
        if (count != null && count > 0) {
            return;   //库里已有文档，跳过（改了 Markdown 想重新灌，手动清空表即可）
        }
        //库里没有文档，才执行读取和入库
        //读取文档
        List<Document> documents = myMarkdownDocumentReader.readMarkdownDocuments();
        //12切割文档
        //List<Document> splitDocuments = myTokenTextSplitter.splitCustomized(documents);
        //13关键词增强
        List<Document> enrichedDocuments = myKeywordMetadataEnricher.enrichDocumentsWithKeywords(documents);
        //vectorStore.add 内部会自动向量化再存储
        vectorStore.add(enrichedDocuments);
    }
}
