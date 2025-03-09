package com.service.RSIranking.repository;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecuritiesStockRepository extends JpaRepository<SecuritiesStockEntity, String> {
    Page<SecuritiesStockEntity> findAll(Pageable pageable);
}
