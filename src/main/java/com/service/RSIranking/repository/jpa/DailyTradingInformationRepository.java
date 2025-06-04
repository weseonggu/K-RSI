package com.service.RSIranking.repository.jpa;

import com.service.RSIranking.entity.DailyTradingInformation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyTradingInformationRepository extends JpaRepository<DailyTradingInformation, Long> {
}
