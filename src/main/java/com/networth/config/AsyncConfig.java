package com.networth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Executor for {@code @Async} work.
 *
 * <p>Wrapped in {@link DelegatingSecurityContextAsyncTaskExecutor} so the caller's
 * {@code SecurityContext} travels with the task. Spring's security context is thread-local,
 * so without this an async method — such as the SSE chat streaming in
 * {@code McpAIChatService.streamChat} — runs anonymously, and anything it touches that
 * consults the authenticated principal fails with an access-denied error that cannot be
 * reported because the streaming response has already been committed.
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    @Bean(name = "asyncExecutor")
    public Executor getAsyncExecutor() {
        return new DelegatingSecurityContextAsyncTaskExecutor(buildExecutor());
    }

    private ThreadPoolTaskExecutor buildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("Async-");
        executor.setKeepAliveSeconds(60);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
