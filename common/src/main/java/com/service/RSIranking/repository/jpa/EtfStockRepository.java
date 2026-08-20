package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.EtfStockInfoEntity;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EtfStockRepository extends JpaRepository<EtfStockInfoEntity, String> {
    Page<StockInfoEntity> findByMktNmAndIsPublicStockTrue(String mktNm, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE EtfStockInfoEntity s SET s.isuNm = :isuNm, s.mktNm = :mktNm, s.isPublicStock = :isPublicStock WHERE s.id = :id")
    int updateStockInfoByCode(@Param("id") String id, @Param("isuNm") String isuNm,
                              @Param("mktNm") String mktNm,
                              @Param("isPublicStock") Boolean isPublicStock);
}
