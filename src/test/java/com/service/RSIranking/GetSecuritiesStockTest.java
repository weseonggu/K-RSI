package com.service.RSIranking;

import com.service.RSIranking.service.SecuritiesStockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class GetSecuritiesStockTest {
    @Autowired
    private SecuritiesStockService securitiesStockService;

    @Test
    public void testGetStockData_Success() throws Exception{
        securitiesStockService.getStockData("20250220");

    }
}
