package com.service.RSIranking.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;


@Service
@RequiredArgsConstructor
public class AsyncJobLanucher {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    @Async("asyncExecutor")
    public CompletableFuture<Void> runKospiInfoJob(JobParameters parameters){
        try {
            jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), parameters);
            return CompletableFuture.completedFuture(null);
        }catch (Exception e){
            return CompletableFuture.failedFuture(e);
        }

    }

    @Async("asyncExecutor")
    public CompletableFuture<Void> runKosdaqInfoJob(JobParameters parameters) throws Exception{
        try {
            jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), parameters);
            return CompletableFuture.completedFuture(null);
        }catch (Exception e){
            return CompletableFuture.failedFuture(e);
        }
    }

    @Async("asyncExecutor")
    public void runKospiTradingJob(JobParameters parameters) throws Exception{
        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), parameters);
    }

    @Async("asyncExecutor")
    public void runKosdaqTradingJob(JobParameters parameters) throws Exception{
        jobLauncher.run(jobRegistry.getJob("dailyTradingInformationUpdateJob"), parameters);
    }
}
