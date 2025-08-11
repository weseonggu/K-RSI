package com.service.RSIranking;

import com.service.RSIranking.repository.jdbc.StockJDBCRepository;
import com.service.RSIranking.service.StockBulkInsertService;
import org.junit.jupiter.api.Disabled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled
public class BulkInsertTest {
    @Autowired
    private StockBulkInsertService stockBulkInsertService;

    @Autowired
    private StockJDBCRepository stockJDBCRepository;

//    @Test
//    void bulkInsertRollbackTest() {
//        // 예제 데이터 생성
//        List<SecuritiesStockEntity> testStocks = List.of(
//                new SecuritiesStockEntity("000006", "Stock A", "KOSPI", true),
//                new SecuritiesStockEntity("000002", "Stock B", "KOSDAQ", true),
//                new SecuritiesStockEntity("000001", "Stock C", "KOSPI", true)
//        );
//
//        // 예외 발생을 기대하면서 Bulk Insert 실행
//        Assertions.assertThrows(RuntimeException.class, () -> {
//            stockBulkInsertService.stocksInsert(testStocks);
//        });
//    }
}
