package com.service.RSIranking;

import com.service.RSIranking.service.SecuritiesStockService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootTest
public class GetSecuritiesStockTest {
    @Autowired
    private SecuritiesStockService securitiesStockService;

    @Test
    public void testGetStockData() throws Exception{
        securitiesStockService.getStockData("20240220");

    }


    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRegistry jobRegistry;

    @Test
    public void testGetStockData_Success() throws Exception{

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), jobParameters);

    }
}
