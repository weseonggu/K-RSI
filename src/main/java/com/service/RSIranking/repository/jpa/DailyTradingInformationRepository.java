package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.KospiDailyTradingInformation;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 일별 매매 정보 JPA 리포지토리.
 *
 * <p>일별 매매 정보에 대한 기본 CRUD 기능을 제공합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
public interface DailyTradingInformationRepository extends JpaRepository<KospiDailyTradingInformation, Long> {
}
