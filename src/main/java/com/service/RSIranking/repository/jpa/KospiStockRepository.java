package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.DailyTradingInformation;
import com.service.RSIranking.entity.KospiStockInfoEntity;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface KospiStockRepository extends JpaRepository<KospiStockInfoEntity, String> {
    Page<KospiStockInfoEntity> findAll(Pageable pageable);
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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE KospiStockInfoEntity s SET s.isuNm = :isuNm, s.mktNm = :mktNm, s.isPublicStock = :isPublicStock WHERE s.id = :id")
    int updateStockInfoByCode(@Param("id") String id,
                              @Param("isuNm") String isuNm,
                              @Param("mktNm") String mktNm,
                              @Param("isPublicStock") Boolean isPublicStock);
}
