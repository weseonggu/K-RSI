package com.service.RSIranking.service;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.KosdaqDailyTradingInformation;
import com.service.RSIranking.entity.KospiDailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 일별 매매 정보 업데이트 서비스.
 *
 * <p>KRX API로부터 수집한 일별 매매 정보를 데이터베이스에 저장하는 서비스입니다.
 * 비동기 처리를 통해 대량의 데이터를 효율적으로 처리합니다.</p>
 *
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>KOSPI 일별 매매 정보 비동기 저장</li>
 *   <li>KOSDAQ 일별 매매 정보 비동기 저장</li>
 *   <li>저장 실패 시 롤백 처리 (재시도 포함)</li>
 * </ul>
 *
 * <h2>비동기 처리</h2>
 * <p>{@code @Async("dailtTrandingExecutor")} 어노테이션을 통해
 * 별도의 스레드 풀에서 비동기로 실행됩니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 * @see DailyTradingInformationJDBCRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateDailyTradingInfoService {

    private final DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    /**
     * 코스피 일별 매매 정보 업데이트
     * @param tradingInfoDtos 일별 매매정보
     */
    @Async("dailtTrandingExecutor")
    public CompletableFuture<Void> kospiTradingInfoInsert(List<KospiDailyTradingInformation> tradingInfoDtos, List<TradingInfoDto> baseDto){
        try{

            dailyTradingInformationJDBCRepository.kospiBulkInsert(tradingInfoDtos, baseDto);
            return CompletableFuture.completedFuture(null);

        }catch (SQLIntegrityConstraintViolationException e){
            log.info("이미 저장된 데이터 입니다.");
            return CompletableFuture.failedFuture(e);
        }
        catch (RuntimeException e){
            log.info("이미 저장된 데이터 입니다.");
            return CompletableFuture.failedFuture(e);
        }
        catch (Exception e){
            log.info("예상치 못한 데이터 저장 문제 발생");
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 코스닥 일별 매매 정보 업데이트
     * @param tradingInfoDtos 일별 매매정보
     */
    @Async("dailtTrandingExecutor")
    public CompletableFuture<Void> kosdaqTradingInfoInsert(List<KosdaqDailyTradingInformation> tradingInfoDtos, List<TradingInfoDto> baseDto){
        try{

            dailyTradingInformationJDBCRepository.kosdaqBulkInsert(tradingInfoDtos, baseDto);
            return CompletableFuture.completedFuture(null);

        }catch (SQLIntegrityConstraintViolationException e){
            log.info("이미 저장된 데이터 입니다.");
            return CompletableFuture.failedFuture(e);
        }
        catch (RuntimeException e){
            log.info("이미 저장된 데이터 입니다.");
            return CompletableFuture.failedFuture(e);
        }
        catch (Exception e){
            log.info("예상치 못한 데이터 저장 문제 발생");
            return CompletableFuture.failedFuture(e);
        }
    }
//=======================================================롤백=========================================================

    /**
     * 코스피 일일 매매 정보 롤백
     * @param date
     */
    @Retryable(recover = "failToRollback",
            retryFor = {
                    RuntimeException.class
            }
            , maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void kospiTradingInfoInsertRollback(String date){
        try{
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate localDate = LocalDate.parse(date, formatter);
            dailyTradingInformationJDBCRepository.kospiInsertRollback(localDate);
        }catch (Exception e){
            throw new RuntimeException("일일 매매 롤백 예외 발생"+e);
        }
    }

    /**
     * 코스닥 일일 매매 정보 롤백
     * @param date
     */
    @Retryable(recover = "failToRollback",
            retryFor = {
                    RuntimeException.class
            }
            , maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void kosdaqTradingInfoInsertRollback(String date){
        try{
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate localDate = LocalDate.parse(date, formatter);
            dailyTradingInformationJDBCRepository.kosdaqInsertRollback(localDate);
        }catch (Exception e){
            throw new RuntimeException("일일 매매 롤백 예외 발생"+e);
        }
    }

    @Recover
    public void failToRollback(RuntimeException e, String date){
        log.info("업데이트 롤백 재시도 실폐");
        throw e;
    }
}
