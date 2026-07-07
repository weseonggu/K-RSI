package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.KosdaqStockInfoEntity;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * KOSDAQ 종목 정보 JPA 리포지토리.
 *
 * <p>KOSDAQ 종목 정보에 대한 CRUD 및 커스텀 쿼리를 제공합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
public interface KosdaqStockRepository extends JpaRepository<KosdaqStockInfoEntity, String> {
    Page<KosdaqStockInfoEntity> findAll(Pageable pageable);
    Page<StockInfoEntity> findByMktNmAndIsPublicStockTrue(String mktNm, Pageable pageable);
    @Query("""
        SELECT t 
        FROM DailyTradingInformation t 
        JOIN FETCH t.stock s 
        WHERE s.id = :isuCd 
        AND t.date = :date
        """)
    Optional<KosdaqStockInfoEntity> findTradingInfoWithStock(
            @Param("isuCd") String isuCd,
            @Param("date") LocalDate date
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE KosdaqStockInfoEntity s SET s.isuNm = :isuNm, s.mktNm = :mktNm, s.isPublicStock = :isPublicStock WHERE s.id = :id")
    int updateStockInfoByCode(@Param("id") String id,
                              @Param("isuNm") String isuNm,
                              @Param("mktNm") String mktNm,
                              @Param("isPublicStock") Boolean isPublicStock);
}
