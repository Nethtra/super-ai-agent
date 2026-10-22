package com.wty.superaiagent.rag;

//import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
//import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetriever;
//import com.alibaba.cloud.ai.dashscope.rag.DashScopeDocumentRetrieverOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;



/**10
 * 配置基于阿里云 DashScope（百炼）云端知识库的 RetrievalAugmentationAdvisor
 * <p>
 * 这是 RAG 的另一种实现思路，与 {@code FitnessAppVectorStoreConfig}（本地方案）对比：
 * <ul>
 *     <li>本地方案：SimpleVectorStore + QuestionAnswerAdvisor ——
 *     文档向量化、存储、检索都在自己应用里完成</li>
 *     <li>云端方案（本类）：文档预先上传到 DashScope 云端知识库，
 *     检索由阿里云托管完成，应用里只负责"发起检索 + 拿结果增强 prompt"，
 *     无需自己维护向量库和 embedding 模型</li>
 * </ul>
 * 前提：需要先在百炼控制台创建名为"健身教练"的知识库并上传文档
 *
 * @author 王天一
 * @version 1.0
 */

//
//@Configuration
//@Slf4j
//public class FitnessAppDashScopeRetrievalAugmentationAdvisorConfig {
//
//    /**
//     * DashScope 平台的 API Key
//     * 从配置（如 application-local.yml 或环境变量）中读取 DASHSCOPE_API_KEY 属性注入
//     */
//    @Value("${DASHSCOPE_API_KEY}")
//    private String dashScopeApiKey;
//
//    /**
//     * 构建云端 RAG 检索增强 Advisor
//     *
//     * @return 注册到 ChatClient 的 defaultAdvisors 后，
//     * 每次对话前会自动到云端知识库检索相关文档片段并拼入 prompt
//     */
//    @Bean
//    public Advisor fitnessAppDashScopeRetrieverAugmentationAdvisor() {
//        //DashScope 的 API 客户端，携带密钥访问阿里云服务
//        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(dashScopeApiKey).build();
//        //云端文档检索器：去指定的知识库（索引）里做语义检索
//        DashScopeDocumentRetriever documentRetriever = new DashScopeDocumentRetriever(dashScopeApi, DashScopeDocumentRetrieverOptions.builder()
//                //检索目标：百炼平台上名为"健身教练"的知识库
//                .withIndexName("健身教练").build());
//        //Spring AI 的 RAG 编排器：拦截请求 → 调用 documentRetriever 检索 → 把结果作为
//        //上下文拼入 prompt → 交给大模型生成，检索增强的完整流程由它统一调度
//        return RetrievalAugmentationAdvisor.builder().documentRetriever(documentRetriever).build();
//    }
//
//}

