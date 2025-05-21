package com.service.RSIranking.service;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.DailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateDailyTradingInfoService {

    private final DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    /**
     * 일별 매매 정보 업데이트
     * @param tradingInfoDtos 일별 매매정보
     */
    @Transactional
    public void tradingInfoInsert(List<DailyTradingInformation> tradingInfoDtos, List<TradingInfoDto> baseDto){
        dailyTradingInformationJDBCRepository.bulkInsert(tradingInfoDtos, baseDto);
    }
}
