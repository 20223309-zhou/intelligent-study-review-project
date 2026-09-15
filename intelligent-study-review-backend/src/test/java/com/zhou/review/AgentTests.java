package com.zhou.review;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.zhou.review.agents.AsyncGenExamPaper;
import com.zhou.review.model.entity.ExamPaper;
import com.zhou.review.model.enums.QuestionConfigEnum;
import com.zhou.review.model.vo.ExamPaperVO;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;

import static com.zhou.review.model.enums.QuestionConfigEnum.*;

@SpringBootTest
@ActiveProfiles("dev")
class AgentTests {
    @Resource
    private OpenAiChatModel openAiChatModel;
    @Resource
    private AsyncGenExamPaper asyncGenExamPaper;

    @Test
    void test() {
        String jsonStr = """
                {
                  "paperName": "大学马克思主义基本原理期末测试卷",
                  "totalScore": 50,
                  "durationMinutes": 90,
                  "questions": [
                    {
                      "sortOrder": 1,
                      "questionType": "SINGLE_CHOICE",
                      "content": "马克思主义理论体系的核心组成部分是？",
                      "options": ["A. 马克思主义哲学", "B. 马克思主义政治经济学", "C. 科学社会主义", "D. 以上都是"],
                      "materialText": null,
                      "score": 1,
                      "answer": "D",
                      "analysis": "马克思主义理论体系包括马克思主义哲学、政治经济学和科学社会主义三大组成部分，三者有机统一。",
                      "difficulty": 1,
                      "knowledgePoints": ["马克思主义的组成部分"]
                    },
                    {
                      "sortOrder": 2,
                      "questionType": "SINGLE_CHOICE",
                      "content": "哲学基本问题是？",
                      "options": ["A. 思维和存在的关系问题", "B. 运动和静止的关系问题", "C. 物质和意识的关系问题", "D. 实践和认识的关系问题"],
                      "materialText": null,
                      "score": 1,
                      "answer": "A",
                      "analysis": "恩格斯指出：“全部哲学，特别是近代哲学的重大的基本问题，是思维和存在的关系问题。”",
                      "difficulty": 1,
                      "knowledgePoints": ["哲学基本问题", "唯物主义与唯心主义"]
                    },
                    {
                      "sortOrder": 3,
                      "questionType": "SINGLE_CHOICE",
                      "content": "唯物辩证法的实质和核心是？",
                      "options": ["A. 联系和发展的观点", "B. 对立统一规律", "C. 量变质变规律", "D. 否定之否定规律"],
                      "materialText": null,
                      "score": 1,
                      "answer": "B",
                      "analysis": "对立统一规律揭示了事物发展的源泉和动力，是唯物辩证法的实质和核心。",
                      "difficulty": 1,
                      "knowledgePoints": ["唯物辩证法", "对立统一规律"]
                    },
                    {
                      "sortOrder": 4,
                      "questionType": "SINGLE_CHOICE",
                      "content": "马克思主义认识论的首要的和基本的观点是？",
                      "options": ["A. 反映的观点", "B. 实践的观点", "C. 真理的观点", "D. 价值的观点"],
                      "materialText": null,
                      "score": 1,
                      "answer": "B",
                      "analysis": "实践是认识的来源、动力、目的和检验标准，是马克思主义认识论的首要观点。",
                      "difficulty": 1,
                      "knowledgePoints": ["实践", "马克思主义认识论"]
                    },
                    {
                      "sortOrder": 5,
                      "questionType": "SINGLE_CHOICE",
                      "content": "社会历史发展的决定力量是？",
                      "options": ["A. 地理环境", "B. 人口因素", "C. 物质资料的生产方式", "D. 社会意识形态"],
                      "materialText": null,
                      "score": 1,
                      "answer": "C",
                      "analysis": "生产方式是社会存在和发展的基础，决定社会性质和面貌，是社会发展的决定力量。",
                      "difficulty": 1,
                      "knowledgePoints": ["历史唯物主义", "生产方式"]
                    },
                    {
                      "sortOrder": 6,
                      "questionType": "SINGLE_CHOICE",
                      "content": "商品的本质属性是？",
                      "options": ["A. 使用价值", "B. 价值", "C. 交换价值", "D. 价格"],
                      "materialText": null,
                      "score": 1,
                      "answer": "B",
                      "analysis": "价值是凝结在商品中的无差别人类劳动，是商品的社会属性，体现商品生产者之间的生产关系，是商品的本质属性。",
                      "difficulty": 2,
                      "knowledgePoints": ["商品", "价值", "使用价值"]
                    },
                    {
                      "sortOrder": 7,
                      "questionType": "SINGLE_CHOICE",
                      "content": "资本主义经济危机的根源在于？",
                      "options": ["A. 生产过剩", "B. 消费不足", "C. 资本主义基本矛盾", "D. 资本主义制度本身"],
                      "materialText": null,
                      "score": 1,
                      "answer": "C",
                      "analysis": "资本主义基本矛盾是生产社会化与生产资料资本主义私人占有之间的矛盾，是经济危机的根本原因。",
                      "difficulty": 2,
                      "knowledgePoints": ["资本主义基本矛盾", "经济危机"]
                    },
                    {
                      "sortOrder": 8,
                      "questionType": "SINGLE_CHOICE",
                      "content": "剩余价值率反映的是？",
                      "options": ["A. 资本家对工人的剥削程度", "B. 预付资本的增殖程度", "C. 资本周转速度", "D. 资本积累规模"],
                      "materialText": null,
                      "score": 1,
                      "answer": "A",
                      "analysis": "剩余价值率是剩余价值与可变资本的比率，m'=m/v，反映资本家对工人的剥削程度。",
                      "difficulty": 2,
                      "knowledgePoints": ["剩余价值率", "剥削程度"]
                    },
                    {
                      "sortOrder": 9,
                      "questionType": "SINGLE_CHOICE",
                      "content": "科学社会主义创立的标志是？",
                      "options": ["A. 《共产党宣言》的发表", "B. 《资本论》的出版", "C. 巴黎公社的建立", "D. 十月革命的胜利"],
                      "materialText": null,
                      "score": 1,
                      "answer": "A",
                      "analysis": "1848年《共产党宣言》的发表标志着马克思主义的诞生，也标志着科学社会主义的创立。",
                      "difficulty": 1,
                      "knowledgePoints": ["科学社会主义", "《共产党宣言》"]
                    },
                    {
                      "sortOrder": 10,
                      "questionType": "SINGLE_CHOICE",
                      "content": "社会主义从空想到科学飞跃的理论基石是？",
                      "options": ["A. 唯物史观和剩余价值学说", "B. 阶级斗争学说", "C. 辩证法", "D. 劳动价值论"],
                      "materialText": null,
                      "score": 1,
                      "answer": "A",
                      "analysis": "唯物史观揭示了社会发展规律，剩余价值学说揭示了资本主义剥削秘密，使社会主义从空想变为科学。",
                      "difficulty": 2,
                      "knowledgePoints": ["社会主义发展史", "唯物史观", "剩余价值学说"]
                    },
                    {
                      "sortOrder": 11,
                      "questionType": "MULTIPLE_CHOICE",
                      "content": "下列属于马克思主义哲学基本特征的有？",
                      "options": ["A. 科学性", "B. 实践性", "C. 阶级性", "D. 革命性", "E. 直观性"],
                      "materialText": null,
                      "score": 2,
                      "answer": "ABCD",
                      "analysis": "马克思主义哲学具有科学性、实践性、阶级性和革命性，直观性是旧唯物主义的特点。",
                      "difficulty": 2,
                      "knowledgePoints": ["马克思主义哲学特征"]
                    },
                    {
                      "sortOrder": 12,
                      "questionType": "MULTIPLE_CHOICE",
                      "content": "价值规律的作用表现在？",
                      "options": ["A. 自发调节生产资料和劳动力在社会各生产部门的分配", "B. 刺激商品生产者改进技术，提高劳动生产率", "C. 导致商品生产者优胜劣汰", "D. 消灭私有制", "E. 消除经济危机"],
                      "materialText": null,
                      "score": 2,
                      "answer": "ABC",
                      "analysis": "价值规律通过价格波动调节资源配置、刺激技术进步、引起两极分化。不能消灭私有制和消除经济危机。",
                      "difficulty": 2,
                      "knowledgePoints": ["价值规律", "市场经济"]
                    },
                    {
                      "sortOrder": 13,
                      "questionType": "SHORT_ANSWER",
                      "content": "简述物质和意识的辩证关系。",
                      "options": null,
                      "materialText": null,
                      "score": 4,
                      "answer": "物质决定意识，意识是对物质的反映；意识对物质具有能动的反作用，正确的意识促进事物发展，错误的意识阻碍事物发展。二者相互区别、相互联系，在实践基础上统一。",
                      "analysis": "物质第一性，意识第二性；意识依赖于物质，同时具有能动性。",
                      "difficulty": 2,
                      "knowledgePoints": ["物质与意识", "辩证关系"]
                    },
                    {
                      "sortOrder": 14,
                      "questionType": "SHORT_ANSWER",
                      "content": "什么是真理的绝对性和相对性？二者关系如何？",
                      "options": null,
                      "materialText": null,
                      "score": 4,
                      "answer": "真理的绝对性是指真理的内容具有客观性，且人类认识无限发展，每获得一个真理就向无限发展着的物质世界接近；真理的相对性是指人们对客观世界的认识总是有限度的、近似的。二者相互渗透、相互包含，绝对真理寓于相对真理之中，相对真理包含着绝对真理的颗粒，人类认识是由相对真理不断逼近绝对真理的过程。",
                      "analysis": "真理是绝对性和相对性的统一，这是真理发展规律。",
                      "difficulty": 2,
                      "knowledgePoints": ["真理", "绝对真理", "相对真理"]
                    },
                    {
                      "sortOrder": 15,
                      "questionType": "SHORT_ANSWER",
                      "content": "简述生产力和生产关系的辩证关系。",
                      "options": null,
                      "materialText": null,
                      "score": 4,
                      "answer": "生产力决定生产关系：生产力的性质决定生产关系的性质，生产力的发展决定生产关系的变革。生产关系对生产力具有反作用：当生产关系适合生产力发展要求时，它就促进生产力发展；当生产关系不适合生产力发展要求时，它就阻碍甚至破坏生产力发展。二者构成社会基本矛盾，推动社会进步。",
                      "analysis": "这是历史唯物主义的核心原理，理解社会发展的根本动力。",
                      "difficulty": 2,
                      "knowledgePoints": ["生产力", "生产关系", "社会基本矛盾"]
                    },
                    {
                      "sortOrder": 16,
                      "questionType": "SHORT_ANSWER",
                      "content": "简述不变资本和可变资本的划分依据及其意义。",
                      "options": null,
                      "materialText": null,
                      "score": 4,
                      "answer": "划分依据是资本在剩余价值生产中所起的不同作用：不变资本（c）是以生产资料形式存在的资本，在生产过程中只转移自身价值，不创造新价值；可变资本（v）是以劳动力形式存在的资本，能创造大于自身价值的剩余价值。意义：揭示了剩余价值的真正来源是可变资本购买的劳动力，从而揭露了资本剥削的秘密。",
                      "analysis": "马克思把资本划分为不变资本和可变资本，是政治经济学的重要贡献。",
                      "difficulty": 2,
                      "knowledgePoints": ["不变资本", "可变资本", "剩余价值来源"]
                    },
                    {
                      "sortOrder": 17,
                      "questionType": "SHORT_ANSWER",
                      "content": "简述资本主义发展的历史趋势。",
                      "options": null,
                      "materialText": null,
                      "score": 4,
                      "answer": "资本主义由于自身基本矛盾（生产社会化与生产资料资本主义私人占有之间的矛盾）的尖锐化，必然导致周期性的经济危机，最终被社会主义所取代。社会主义代替资本主义是生产社会化的客观要求，是资本主义基本矛盾运动的必然结果，是一个长期的、曲折的历史过程。",
                      "analysis": "科学社会主义揭示了资本主义必然灭亡、社会主义必然胜利的历史规律。",
                      "difficulty": 2,
                      "knowledgePoints": ["资本主义历史趋势", "社会主义必然性"]
                    },
                    {
                      "sortOrder": 18,
                      "questionType": "ESSAY",
                      "content": "试论马克思主义的实践性及其在当代中国的指导意义。",
                      "options": null,
                      "materialText": null,
                      "score": 8,
                      "answer": "马克思主义的实践性体现在：第一，实践是马克思主义理论的出发点和归宿，马克思主义从实践中产生，在实践中发展，并以指导实践为目的。第二，实践是检验真理的唯一标准，马克思主义理论必须经得起实践的检验，并在实践中不断丰富和发展。第三，马克思主义强调理论联系实际，反对教条主义和经验主义。在当代中国，马克思主义的实践性指导意义体现为：坚持解放思想、实事求是、与时俱进，将马克思主义基本原理与中国具体实际相结合，形成中国特色社会主义理论体系；以实践为基础不断推进理论创新，指导改革开放和社会主义现代化建设；在实践中坚持和发展马克思主义，推进国家治理体系和治理能力现代化。总之，马克思主义不是僵化的教条，而是行动的指南，只有与时代和实践紧密结合，才能焕发强大的生命力。",
                      "analysis": "该题要求论述马克思主义实践性特征及其当代价值，需要结合理论要点和现实应用。",
                      "difficulty": 3,
                      "knowledgePoints": ["马克思主义实践性", "理论联系实际", "中国特色社会主义"]
                    },
                    {
                      "sortOrder": 19,
                      "questionType": "ESSAY",
                      "content": "运用唯物辩证法联系和发展的观点，分析当前全球气候变化问题的成因及应对策略。",
                      "options": null,
                      "materialText": null,
                      "score": 8,
                      "answer": "唯物辩证法认为，世界上一切事物都处于普遍联系和永恒发展之中。联系具有客观性和多样性，发展是新事物的产生和旧事物的灭亡。全球气候变化问题的成因：从联系的观点看，气候变化与人类活动（如工业排放、森林砍伐、化石燃料燃烧等）密切相关，也与自然因素（太阳辐射、火山活动等）相互影响。从发展的观点看，工业革命以来人类对自然的过度索取导致温室气体浓度上升，破坏了地球系统的动态平衡，引发了气候变暖、极端天气频发等问题。应对策略：第一，坚持整体性思维，认识到气候变化是全球性问题，需要各国共同行动，构建人类命运共同体。第二，坚持矛盾分析方法，既要发展经济，又要保护环境，走绿色低碳发展之路。第三，坚持质量互变规律，从减少排放的小事做起，逐步实现碳中和目标。第四，坚持否定之否定规律，推动生产方式和生活方式向生态化转型。总之，只有运用唯物辩证法，才能科学认识气候变化并采取有效应对措施。",
                      "analysis": "本题要求运用唯物辩证法原理分析实际问题，考查理论联系实际的能力。",
                      "difficulty": 3,
                      "knowledgePoints": ["唯物辩证法", "联系的观点", "发展的观点", "气候变化"]
                    }
                  ]
                }
                """;
        ExamPaperVO bean = JSONUtil.toBean(jsonStr, ExamPaperVO.class);
        System.out.println(bean.toString());
    }

    @Test
    void agentsTest(){
        ExamPaper examPaper = ExamPaper.builder()
                .subject("马克思主义基本原理")
                .gradeOrLevel("大学")
                .questionConfig(Map.of(
                        SINGLE_CHOICE.getText(), 10,
                        MULTIPLE_CHOICE.getText(), 2,
                        SHORT_ANSWER.getText(), 5,
                        ESSAY.getText(), 2
                ))
                .textbookVersion("高等教育出版社2023版")
                .durationMinutes(90)
                .totalScore(new BigDecimal(50))
                .build();
        asyncGenExamPaper.generateExamPaper(examPaper);
    }

    @Test
    void contextLoads() {
        // 创建专业化的子Agent
        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(openAiChatModel)
                .description("专业写作Agent")
                .instruction("你是一个知名的作家，擅长写作和创作。请根据用户的提问进行回答：{input}。")
                .outputKey("article")
                .build();

        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer_agent")
                .model(openAiChatModel)
                .description("专业评审Agent")
                .instruction("你是一个知名的评论家，擅长对文章进行评论和修改。" +
                        "对于散文类文章，请确保文章中必须包含对于西湖风景的描述。待评论文章： {article}" +
                        "最终只返回修改后的文章，不要包含任何评论信息。")
                .outputKey("reviewed_article")
                .build();

        // 创建顺序Agent
        SequentialAgent blogAgent = SequentialAgent.builder()
                .name("blog_agent")
                .description("根据用户给定的主题写一篇文章，然后将文章交给评论员进行评论")
                .subAgents(List.of(writerAgent, reviewerAgent))
                .build();
        Flux<NodeOutput> result;
        // 使用
        try {
            result = blogAgent.stream("帮我写一个100字左右的散文");
        } catch (GraphRunnerException e) {
            throw new RuntimeException(e);
        }
        result.doOnNext(output ->{
            // 1. 判断是否为流式输出对象
            if (output instanceof StreamingOutput<?> streamingOutput) {
                // 2. 获取消息体
                var message = streamingOutput.message();

                // 3. 判断是否为助手消息（AI返回的消息），且不为空
                if (message instanceof AssistantMessage assistantMessage) {
                    String text = assistantMessage.getText();
                    // 打印出当前是哪个节点在输出
                    String nodeName = output.agent();
                    if (text != null && !text.isEmpty()) {
                        System.out.println("[" + nodeName + "] " + text);
                    }
                }
            }
        }).blockLast();
//        if (result.isPresent()) {
//            OverAllState state = result.get();
//
//            // 访问第一个Agent的输出
//            state.value("article").ifPresent(article -> {
//                if (article instanceof AssistantMessage) {
//                    System.out.println("原始文章: " + ((AssistantMessage) article).getText());
//                }
//            });
//
//            // 访问第二个Agent的输出
//            state.value("reviewed_article").ifPresent(reviewedArticle -> {
//                if (reviewedArticle instanceof AssistantMessage) {
//                    System.out.println("评审后文章: " + ((AssistantMessage) reviewedArticle).getText());
//                }
//            });
//        }
    }
}
