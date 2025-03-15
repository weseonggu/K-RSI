package com.service.RSIranking.batch.step;

import com.service.RSIranking.entity.SecuritiesStockEntity;
import com.service.RSIranking.repository.jpa.SecuritiesStockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.annotation.BeforeStep;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemStreamException;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Iterator;

@RequiredArgsConstructor
public class DBStockReader implements ItemReader<SecuritiesStockEntity>, ItemStreamReader<SecuritiesStockEntity> {

    private final SecuritiesStockRepository securitiesStockRepository;

    private StepExecution stepExecution;
    private String mktNm;

    private int currentPage = 0;
    private Iterator<SecuritiesStockEntity> currentIterator = null;

    @Override
    public SecuritiesStockEntity read() throws Exception {
        if (currentIterator == null || !currentIterator.hasNext()) {
            // 새 페이지 로드
            // todo 페이징 크기 chunk 크기와 같아야 하기 때문에 yml파일에서 관리하도록 변경이 필요
            Page<SecuritiesStockEntity> currentBatch = securitiesStockRepository.findByMktNm(mktNm, PageRequest.of(currentPage, 10));

            if (currentBatch.isEmpty()) {
                return null; // 더 이상 읽을 데이터 없음
            }

            currentIterator = currentBatch.iterator();
            currentPage++; // 다음 페이지로 이동
        }

        return currentIterator.hasNext() ? currentIterator.next() : null;
    }

    @BeforeStep
    public void saveStepExecution(StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        JobParameters jobParameters = stepExecution.getJobParameters();
        this.mktNm = jobParameters.getString("mktNm");
    }

    @Override
    public void open(ExecutionContext executionContext) throws ItemStreamException {
        this.currentPage = executionContext.getInt("currentPage", 0);
    }

    @Override
    public void update(ExecutionContext executionContext) throws ItemStreamException {
        executionContext.putInt("currentPage", currentPage);
    }

    @Override
    public void close() throws ItemStreamException {
        this.currentIterator = null;
    }
}


