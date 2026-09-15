package com.zhou.review.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 试卷生成专用线程池。
 * <p>
 * 生成要跑几十秒到几分钟（最多 3 轮多 Agent 循环 + 外部大模型调用），
 * 必须与 Tomcat 请求线程隔离：
 * <ul>
 *   <li>SSE 端点必须立刻返回 emitter，生成得挪到别的线程；</li>
 *   <li>否则并发几个生成请求就把 Web 线程池占满，整个站点的接口一起变慢。</li>
 * </ul>
 *
 * @author zhou
 */
@Configuration
public class GenerationAsyncConfig {

    @Bean("generationExecutor")
    public ThreadPoolTaskExecutor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("exam-gen-");
        // 池满时直接拒绝，由接口层转成一条 error 事件 ——
        // 比默默排队到用户超时友好，也便于排查"点了没反应"。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
