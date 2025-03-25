package com.service.RSIranking.batch.step;

import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.KrxRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

@RequiredArgsConstructor
public class RequestDailyTradingInfoTasklet implements Tasklet {

    private final KrxRequestService krxRequestService;
    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedisService;


    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        return null;
    }
}
