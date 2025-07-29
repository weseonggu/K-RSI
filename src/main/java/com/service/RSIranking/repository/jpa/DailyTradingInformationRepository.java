package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.KospiDailyTradingInformation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTradingInformationRepository extends JpaRepository<KospiDailyTradingInformation, Long> {
}
