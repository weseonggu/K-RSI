package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.StockInfoEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecuritiesStockRepository extends JpaRepository<StockInfoEntity, String> {
    Page<StockInfoEntity> findAll(Pageable pageable);
    Page<StockInfoEntity> findByMktNmAndIsPublicStockTrue(String mktNm, Pageable pageable);
}
