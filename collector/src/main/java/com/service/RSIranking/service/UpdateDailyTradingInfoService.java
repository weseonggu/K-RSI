package com.service.RSIranking.service;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.inter.DailyTradingInformation;
import com.service.RSIranking.repository.jdbc.DailyTradingInformationJDBCRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 일별 매매 정보 업데이트 서비스. (KOSPI/KOSDAQ 공용)
 *
 * <p>KRX API로부터 수집한 일별 매매 정보를 데이터베이스에 저장하는 서비스입니다.
 * 비동기 처리를 통해 대량의 데이터를 효율적으로 처리합니다.</p>
 *
 * <h2>멱등성 / 예외 처리</h2>
 * <ul>
 *   <li>삽입은 ON DUPLICATE KEY no-op으로 멱등 - 이미 저장된 데이터는 조용히 건너뜀</li>
 *   <li>중복 키 예외(DuplicateKeyException)는 정상 처리로 간주</li>
 *   <li>그 외 예외(커넥션 장애 등)는 error 로그 후 실패로 전파
 *       (과거에는 모든 예외가 "이미 저장된 데이터"로 로깅되어 실제 장애가 은폐되었음)</li>
 * </ul>
 *
 * <h2>비동기 처리</h2>
 * <p>{@code @Async("dailtTrandingExecutor")} 어노테이션을 통해
 * 별도의 스레드 풀에서 비동기로 실행됩니다.</p>
 *
 * @author RSIranking Team
 * @version 2.0
 * @see DailyTradingInformationJDBCRepository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateDailyTradingInfoService {

    private final DailyTradingInformationJDBCRepository dailyTradingInformationJDBCRepository;

    /**
     * 일별 매매 정보를 비동기로 저장합니다.
     *
     * @param tradingInfoEntities 저장할 매매 정보 엔티티 목록
     * @param baseDto 원본 DTO 목록 (종목 코드 참조용)
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ")
     * @return 저장 결과 Future (실패 시 failedFuture)
     */
    @Async("dailtTrandingExecutor")
    public CompletableFuture<Void> tradingInfoInsert(List<? extends DailyTradingInformation> tradingInfoEntities,
                                                     List<TradingInfoDto> baseDto, String mktNm) {
        try {
            dailyTradingInformationJDBCRepository.bulkInsert(tradingInfoEntities, baseDto, mktNm);
            return CompletableFuture.completedFuture(null);
        } catch (DuplicateKeyException e) {
            // ON DUPLICATE KEY no-op 이후 도달할 일이 거의 없지만, 도달해도 멱등 관점에서 정상 처리
            log.info("{}: 이미 저장된 데이터가 포함되어 있습니다. (건너뜀)", mktNm);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("{}: 매매 정보 저장 실패 - {}", mktNm, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    //=======================================================롤백=========================================================

    /**
     * 일일 매매 정보 롤백. (KOSPI/KOSDAQ 공용)
     *
     * <p>해당 날짜에 삽입된 시장 전체 행을 삭제하므로,
     * 진짜 삽입 오류로 데이터가 부분 적재된 경우에만 호출해야 합니다.</p>
     *
     * @param date 롤백할 날짜 (yyyyMMdd)
     * @param mktNm 시장 구분 ("KOSPI" / "KOSDAQ")
     */
    @Retryable(recover = "failToRollback",
            retryFor = {
                    RuntimeException.class
            }
            , maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public void tradingInfoInsertRollback(String date, String mktNm) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate localDate = LocalDate.parse(date, formatter);
            dailyTradingInformationJDBCRepository.insertRollback(localDate, mktNm);
        } catch (Exception e) {
            throw new RuntimeException("일일 매매 롤백 예외 발생" + e);
        }
    }

    @Recover
    public void failToRollback(RuntimeException e, String date, String mktNm) {
        log.error("{}: 매매 정보 롤백 재시도 실패 - 날짜: {}", mktNm, date, e);
        throw e;
    }
}
