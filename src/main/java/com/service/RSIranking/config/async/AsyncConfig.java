package com.service.RSIranking.config.async;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.aop.interceptor.SimpleAsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 비동기 처리 설정 클래스.
 *
 * <p>{@code @Async} 어노테이션을 활성화하고, 비동기 작업을 위한 스레드 풀을 구성합니다.</p>
 *
 * <h2>제공 Executor</h2>
 * <ul>
 *   <li><b>asyncExecutor</b>: 기본 비동기 작업용 (배치 Job 실행)</li>
 *   <li><b>dailtTrandingExecutor</b>: 일별 매매 정보 저장 전용</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
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

    /**
     * 비동기 작업 중 발생한 예외를 처리하는 핸들러를 반환합니다.
     *
     * @return 예외 핸들러
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new SimpleAsyncUncaughtExceptionHandler();
    }
}