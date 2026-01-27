package com.service.RSIranking.batch.stock_info_job.step;

import com.fasterxml.jackson.core.type.TypeReference;
import com.service.RSIranking.dto.StockDto;

import com.service.RSIranking.entity.KosdaqStockInfoEntity;
import com.service.RSIranking.entity.KospiStockInfoEntity;
import com.service.RSIranking.entity.inter.StockInfoEntity;
import com.service.RSIranking.service.InterStepDataSharingWithRedisService;
import com.service.RSIranking.service.StockBulkInsertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 종목 정보 비교 및 업데이트 Processor.
 *
 * <p>KRX API에서 가져온 종목 정보와 DB의 기존 정보를 비교하여
 * 변경사항을 처리합니다.</p>
 *
 * <h2>처리 로직</h2>
 * <ul>
 *   <li>종목명 변경: 엔티티 업데이트 후 반환</li>
 *   <li>변경 없음: null 반환 (Writer 스킵)</li>
 *   <li>상장폐지: 상장 상태 false로 변경</li>
 *   <li>신규 상장: afterStep에서 벌크 삽입 처리</li>
 * </ul>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@StepScope
@Component
@RequiredArgsConstructor
@Slf4j
public class CompareAndUpdateProcessor implements ItemProcessor<StockInfoEntity, StockInfoEntity>, StepExecutionListener {

    private List<StockDto> dtoList;
    private int del =0;
    private String mktNm;

    private final InterStepDataSharingWithRedisService interStepDataSharingWithRedis;
    private final StockBulkInsertService stockBulkInsertService;


    /**
     * Step 실행 전 Redis에서 KRX 데이터를 조회합니다.
     *
     * @param stepExecution Step 실행 정보
     */
    @Override
    public void beforeStep(StepExecution stepExecution) {
        try {
            final JobExecution jobExecution = stepExecution.getJobExecution();
            final ExecutionContext jobContext = jobExecution.getExecutionContext();
            String redisKey = (String) jobContext.get("StockDtoList");

            JobParameters jobParameters = stepExecution.getJobParameters();
            this.mktNm = jobParameters.getString("mktNm");

            this.dtoList = interStepDataSharingWithRedis
                    .getStockToRedis(redisKey, new TypeReference<List<StockDto>>() {})
                    .orElseThrow(() -> new RuntimeException("Redis에서 StockDtoList를 찾을 수 없습니다."));
        } catch (Exception e) {
            stepExecution.setStatus(BatchStatus.FAILED);
            throw new RuntimeException("중간 단계 데이터 조회 중 예외 발생", e);
        }
    }

    /**
     * DB 종목 정보와 KRX 데이터를 비교하여 처리합니다.
     *
     * @param entity DB의 종목 정보 엔티티
     * @return 변경된 엔티티 또는 null (변경 없음)
     * @throws Exception 처리 중 예외 발생 시
     */
    @Override
    public StockInfoEntity process(StockInfoEntity entity) throws Exception {
        // dtoList와 비교하여 변경 사항 처리
        // todo dtoList가 null일 경우의 처리를 해야 함
        Optional<StockDto> matchedDto = dtoList.stream()
                .filter(dto -> dto.getIsuCd().equals(entity.getId()))
                .findFirst();

        if (matchedDto.isPresent()) {
            // 확인한 dto true로 변경
            matchedDto.ifPresent(StockDto::updateChecked);

            if (compareStockData(entity, matchedDto.get())){
                // 기존 데이터 업데이트
                log.info(entity.getId() + "변경사항 업데이트");
                entity.updateFromDto(matchedDto.get());
            }
            else{
                log.info(entity.getId() + "변경사항 없음 null 반환");
                return null;
            }


            return entity;
        } else {
            // 삭제된 데이터 처리
            log.info(entity.getId()+" 상장폐지");
            entity.delistStock();
            del++;
            return entity;
        }
    }
    /**
     * 종목 정보 변경 여부를 비교합니다.
     *
     * @param entity DB 엔티티
     * @param dto    KRX DTO
     * @return 변경 여부 (true: 변경됨)
     */
    private boolean compareStockData(StockInfoEntity entity, StockDto dto){
        return !entity.getIsuNm().equals(dto.getIsuNm());
    }

    /**
     * Step 완료 후 신규 종목을 벌크 삽입합니다.
     *
     * <p>KRX 데이터 중 DB에 없는 종목(checked=false)을 신규 종목으로 판단하여
     * 대량 삽입합니다.</p>
     *
     * @param stepExecution Step 실행 정보
     * @return Step 종료 상태
     */
    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {

        if (stepExecution.getExitStatus().getExitCode().equals(ExitStatus.FAILED.getExitCode())) {
            return ExitStatus.FAILED;
        }

        List<StockDto> newStockDtos = dtoList.stream()
                .filter(dto -> !dto.isChecked())
                .collect(Collectors.toList());
        if(newStockDtos.isEmpty()){
            log.info(mktNm + ": 종목: "+ del + "개 폐지");
            log.info(mktNm + ": 신규 종목 추가 없음");
            return ExitStatus.COMPLETED;
        }
        List<StockInfoEntity> newStockEntities = null;
        // 코스피, 코스닥 분기 처리
        if(mktNm.equals("KOSPI")){
            newStockEntities = newStockDtos.stream()
                    .map(KospiStockInfoEntity::new)
                    .collect(Collectors.toList());
            if (!newStockEntities.isEmpty()) {
                stockBulkInsertService.KospiStocksInsert(newStockEntities);
            }
        }else{
            newStockEntities = newStockDtos.stream()
                    .map(KosdaqStockInfoEntity::new)
                    .collect(Collectors.toList());
            if (!newStockEntities.isEmpty()) {
                stockBulkInsertService.KosdaqStocksInsert(newStockEntities);
            }
        }
        log.info(mktNm+": 종목: "+ del + "개 폐지");
        log.info(mktNm+": 신규 종목 추가: "+ newStockEntities.size() + "개 추가");


        return ExitStatus.COMPLETED;
    }

}
