package com.zhou.review.config;

import com.zhou.review.annotation.AuthCheck;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 基于JDBC存储的ChatMemory配置
 */
@Configuration
public class ChatMemoryConfig {
    @Autowired
    JdbcChatMemoryRepository chatMemoryRepository;
    @Bean
    public ChatMemory chatMemory() {
        // 使用 MessageWindowChatMemory 并指定最大消息数
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }
}