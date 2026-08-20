package com.service.RSIranking.batch.stock_info_job.step;


import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.repository.jpa.KosdaqStockRepository;
import com.service.RSIranking.repository.jpa.KospiStockRepository;
import com.service.RSIranking.repository.jpa.EtfStockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Iterator;

/**
 * 데이터베이스에서 종목 정보를 읽어오는 Reader.
 *
 * <p>KOSPI/KOSDAQ 종목 정보를 페이징 처리하여 읽어옵니다.
 * Job 파라미터의 시장 구분(mktNm)에 따라 해당 시장의 종목만 조회합니다.</p>
 *
 * <h2>페이징 처리</h2>
 * <ul>
 *   <li>페이지 크기: 50 (청크 크기와 동일)</li>
 *   <li>상장 종목만 조회 (isPublicStock=true)</li>
 * </ul>
 *
 * <h2>재시작 지원</h2>
 * <p>ItemStreamReader 구현을 통해 배치 재시작 시 마지막 처리 페이지부터 재개합니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class GetStockInfoToDBReader implements ItemReader<StockInfoEntity>, ItemStreamReader<StockInfoEntity>, StepExecutionListener {

    private StepExecution stepExecution;
    private String mktNm;
    private int currentPage = 0;
    private Iterator<StockInfoEntity> currentIterator = null;

    private final KospiStockRepository kospiStockRepository;
    private final KosdaqStockRepository kosdaqStockRepository;
    private final EtfStockRepository etfStockRepository;
    private int pageSize;

    /**
     * 다음 종목 정보를 읽어옵니다.
     *
     * <p>현재 페이지의 데이터를 모두 반환하면 다음 페이지를 로드합니다.
     * 더 이상 데이터가 없으면 null을 반환하여 읽기를 종료합니다.</p>
     *
     * @return 종목 정보 엔티티 또는 null (데이터 없음)
     * @throws Exception 읽기 중 예외 발생 시
     */
    @Override
    public StockInfoEntity read() throws Exception {
        if (currentIterator == null || !currentIterator.hasNext()) {
            // 새 페이지 로드
            // todo 페이징 크기 chunk 크기와 같아야 하기 때문에 yml파일에서 관리하도록 변경이 필요
            Page<StockInfoEntity> currentBatch = null;
            // 코스피, 코스닥 분기 처리
            currentBatch = switch (mktNm.toUpperCase()) {
                case "KOSPI" -> kospiStockRepository.findByMktNmAndIsPublicStockTrue(mktNm, PageRequest.of(currentPage, pageSize));
                case "KOSDAQ" -> kosdaqStockRepository.findByMktNmAndIsPublicStockTrue(mktNm, PageRequest.of(currentPage, pageSize));
                case "ETF" -> etfStockRepository.findByMktNmAndIsPublicStockTrue(mktNm, PageRequest.of(currentPage, pageSize));
                default -> throw new IllegalArgumentException("지원하지 않는 시장: " + mktNm);
            };



            if (currentBatch.isEmpty()) {
                return null; // 더 이상 읽을 데이터 없음
            }

            currentIterator = currentBatch.iterator();
            currentPage++; // 다음 페이지로 이동
        }
        return currentIterator.hasNext() ? currentIterator.next() : null;
    }

    /**
     * Step 실행 전 시장 구분과 페이지 크기를 설정합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        JobParameters jobParameters = stepExecution.getJobParameters();
        this.mktNm = jobParameters.getString("mktNm");
        this.pageSize = 50; // todo 임시 청크 사이즈
    }

    /**
     * 스트림을 열고 이전 실행 상태를 복원합니다.
     *
     * @param executionContext 실행 컨텍스트
     * @throws ItemStreamException 스트림 열기 실패 시
     */
    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.currentPage = executionContext.getInt("currentPage", 0);
    }

    /**
     * 현재 페이지 정보를 실행 컨텍스트에 저장합니다.
     *
     * @param executionContext 실행 컨텍스트
     * @throws ItemStreamException 업데이트 실패 시
     */
    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt("currentPage", currentPage);
    }

    /**
     * 스트림을 닫고 리소스를 해제합니다.
     *
     * @throws ItemStreamException 스트림 닫기 실패 시
     */
    @Override
    public void close() throws ItemStreamException {
        this.currentIterator = null;
    }
}


