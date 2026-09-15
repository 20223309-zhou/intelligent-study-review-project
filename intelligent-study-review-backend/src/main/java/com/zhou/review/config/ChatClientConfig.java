package com.zhou.review.config;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 初始化ChatClient
 */
@Configuration
public class ChatClientConfig {
    @Resource
    private ChatMemory chatMemory;
    @Resource
    private ChatClient.Builder chatClientBuilder;

    @Bean
    public ChatClient chatClient() {

        return chatClientBuilder
                // 添加默认的顾问
                .defaultAdvisors(
                        // 添加内存顾问记录对话历史
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        //添加日志顾问记录请求和响应元数据
                        SimpleLoggerAdvisor.builder()
                                .requestToString(request -> "Request: " + request.prompt().getUserMessage())
                                .responseToString(response -> "Response: " +
                                        "输入token:" + response.getMetadata().getUsage().getPromptTokens() +
                                        "，输出token:" + response.getMetadata().getUsage().getCompletionTokens() +
                                        "，总token:" + response.getMetadata().getUsage().getTotalTokens())
                                .build())
                .build();
    }
}
