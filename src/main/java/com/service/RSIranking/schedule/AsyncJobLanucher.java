package com.service.RSIranking.schedule;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AsyncJobLanucher {

    private final JobLauncher jobLauncher;
    private final JobRegistry jobRegistry;

    @Async("asyncExecutor")
    public void runKospiJob(JobParameters parameters) throws Exception{
        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), parameters);
    }

    @Async("asyncExecutor")
    public void runKosdaqJob(JobParameters parameters) throws Exception{
        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), parameters);
    }
}
