package com.zhou.review;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

@SpringBootTest
@ActiveProfiles("dev")
class IntelligentStudyReviewBackendApplicationTests {
    @Resource
    private ChatClient chatClient;
    @Resource
    private OpenAiChatModel openAiChatModel;
    @Test
    void contextLoads() {
//        chatClient.prompt()
//                .user("什么是spring cloud?50个字内说明白")
//                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, "666"))
//                .stream()
//                .content()
//                .doOnNext(System.out::print)
//                .then()
//                .block();

        ReactAgent agent = ReactAgent.builder()
                .name("agent1")
                .model(openAiChatModel)
                .build();
        try {
            AssistantMessage response = agent.call(new UserMessage("你是谁呀？"));
            System.out.println(response);
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
    }

}
