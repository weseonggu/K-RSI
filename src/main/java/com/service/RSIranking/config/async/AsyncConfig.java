package com.service.RSIranking.config.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {
    /**
     * 기본 Async 쓰레드 풀
     * @return
     */
    @Override
    @Bean(name = "asyncExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);        // 기본 스레드 수
        executor.setMaxPoolSize(10);        // 최대 스레드 수
        executor.setQueueCapacity(25);      // 대기 큐 크기
        executor.setThreadNamePrefix("StockInfo-");  // 스레드 이름 prefix
        executor.initialize();
        return executor;
    }

    // todo 추후 배포 환경에서는 쓰레드 풀 조절 가능하도록 병견 필요
    /**
     * 매매 정보 업데이트에서만 사용할 Async 쓰레드 풀
     * @return
     */
    @Bean(name = "dailtTrandingExecutor")
    public Executor dailtTrandingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(40); // 매매정보가 하루에 2800여개 정도니 100개씩 해도 최소 29개는 필요한 상황 그러니 넉넉하게 설정 필요
        executor.setThreadNamePrefix("ProductInfo-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}