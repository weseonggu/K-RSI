package com.service.RSIranking;

import com.service.RSIranking.service.SecuritiesStockService;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.text.SimpleDateFormat;
import java.util.Date;

@SpringBootTest
public class GetSecuritiesStockTest {
    @Autowired
    private SecuritiesStockService securitiesStockService;

//    @Test
//    public void testGetStockData() throws Exception{
//        securitiesStockService.getStockData("20240220");
//
//    }


    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private JobRegistry jobRegistry;

    @Value("${api.krx.key}")
    private String key;

    @Value("${api.krx.kospi-info-url}")
    private String kospiInfoUrl;

    @Test
    public void getKospiStockData_Success() throws Exception {

        System.gc();
        Thread.sleep(1000);

        Runtime runtime = Runtime.getRuntime();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", kospiInfoUrl)
                .addString("apiKey", key)
                .addString("mktNm", "KOSPI")
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), jobParameters);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.gc();
        Thread.sleep(1000);

        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedMemory = afterMemory - beforeMemory;

        System.out.println("배치 실행 시간(ms): " + duration);
        System.out.println("배치 실행 시 사용된 메모리: " + usedMemory + " bytes");
    }


    @Value("${api.krx.kosdaq-info-url}")
    private String kosdaqInfoUrl;

    @Test
    public void getKosdaqStockData_Success() throws Exception{

        System.gc();
        Thread.sleep(1000);

        Runtime runtime = Runtime.getRuntime();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        long startTime = System.currentTimeMillis();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-hh-mm-ss");
        String date = dateFormat.format(new Date());

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("date", date)
                .addString("apiUrl", kosdaqInfoUrl)
                .addString("apiKey", key)
                .addString("mktNm", "KOSDAQ")
                .toJobParameters();

        jobLauncher.run(jobRegistry.getJob("stockUpdateJob"), jobParameters);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        System.gc();
        Thread.sleep(1000);

        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long usedMemory = afterMemory - beforeMemory;

        System.out.println("배치 실행 시간(ms): " + duration);
        System.out.println("배치 실행 시 사용된 메모리: " + usedMemory + " bytes");

    }
}
