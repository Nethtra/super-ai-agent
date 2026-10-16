package com.wty.superaiagent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatMemory 配置类：把对话记忆的存储方式从内存切换为 JDBC（数据库持久化）
 * <p>
 * 对比两种方式：
 * <ul>
 *     <li>默认方式（MessageWindowChatMemory.builder().build()）：
 *     使用内存中的 InMemoryChatMemoryRepository，应用重启后对话记忆全部丢失</li>
 *     <li>本配置（注入 JdbcChatMemoryRepository）：
 *     历史消息写入数据库（表结构由 spring-ai-starter 的 schema 自动创建），
 *     重启后同一 conversationId 仍能接上之前的对话</li>
 * </ul>
 * <p>
 * 本类定义了 ChatMemory 的 @Bean 后，容器中所有注入 ChatMemory 的地方
 * （如通过构造器把 ChatMemory 传给 MessageChatMemoryAdvisor 的 ChatClient）
 * 都会统一使用这个 JDBC 持久化实现，避免各组件各自 new 导致存储分散
 * <p>
 * 需要在 pom.xml 引入 spring-ai-starter-model-chat-memory-repository-jdbc
 * 及对应数据库驱动（如 MySQL/PostgreSQL），并配置好数据源
 *
 * @author 王天一
 * @version 1.0
 */

@Configuration
public class ChatMemoryConfig {

    /**
     * 创建基于 JDBC 持久化的对话记忆 Bean
     *
     * @param repository JDBC 实现的存储仓库，由 starter 自动装配（根据数据源配置创建）
     * @return 窗口大小为 20 条、数据落库的 ChatMemory
     */
    @Bean
    public ChatMemory chatMemory(JdbcChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                //指定历史消息的实际存储位置：数据库，而非默认的内存
                .chatMemoryRepository(repository)
                //窗口大小：每个会话最多保留最近 20 条消息，超出后自动淘汰最早的
                .maxMessages(20)
                .build();
    }
}
