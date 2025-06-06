package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.DailyTradingInformation;
import com.service.RSIranking.entity.StockInfoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface SecuritiesStockRepository extends JpaRepository<StockInfoEntity, String> {
    Page<StockInfoEntity> findAll(Pageable pageable);
    Page<StockInfoEntity> findByMktNmAndIsPublicStockTrue(String mktNm, Pageable pageable);
    @Query("""
        SELECT t 
        FROM DailyTradingInformation t 
        JOIN FETCH t.stock s 
        WHERE s.id = :isuCd 
        AND t.date = :date
        """)
    Optional<DailyTradingInformation> findTradingInfoWithStock(
            @Param("isuCd") String isuCd,
            @Param("date") LocalDate date
    );
}
