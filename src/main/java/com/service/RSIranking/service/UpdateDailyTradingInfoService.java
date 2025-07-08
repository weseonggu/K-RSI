package com.service.RSIranking.service;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.DailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    @Async("dailtTrandingExecutor")
    public CompletableFuture<Void> tradingInfoInsert(List<DailyTradingInformation> tradingInfoDtos, List<TradingInfoDto> baseDto) throws Exception {
        try{
            dailyTradingInformationJDBCRepository.bulkInsert(tradingInfoDtos, baseDto);
            return CompletableFuture.completedFuture(null);
        }catch (SQLIntegrityConstraintViolationException e){
            log.info("이미 저장된 데이터 입니다.");
            return CompletableFuture.failedFuture(e);
        }
        catch (Exception e){
            return CompletableFuture.failedFuture(e);
        }
    }
}
