package com.service.RSIranking.repository.jdbc;

import com.service.RSIranking.dto.TradingInfoDto;
import com.service.RSIranking.entity.DailyTradingInformation;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.IntStream;

@Repository
public class DailyTradingInformationJDBCRepository {
    private final JdbcTemplate jdbcTemplate;

    public DailyTradingInformationJDBCRepository(@Qualifier("jdbcDataTemplate") JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;

    }

    public void bulkInsert(List<DailyTradingInformation> newTradingInfo, List<TradingInfoDto> baseInfoDtos){
        // todo 부모 테이블에 키가 있는지 확인하고 저장하는 쿼리 작성
        String sql =
//                """
//                INSERT INTO DailyTradingInformation
//                (date, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, acc_trdvol, acc_trdval, isu_cd)
//                SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ? WHERE EXISTS
//                ( SELECT isu_cd FROM SecuritiesStockEntity s WHERE s.isu_cd = '?')
//                """;
                "INSERT INTO DailyTradingInformation (`date`, tdd_clsprc, cmpprevdd_prc, fluc_rt, tdd_opnprc, tdd_hgprc, tdd_lwprc, acc_trdvol, acc_trdval, isu_cd) SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, s.isu_cd FROM SecuritiesStockEntity s WHERE s.isu_cd = ?";
        List<Object[]> batchArgs = IntStream.range(0, newTradingInfo.size())
                .mapToObj(i -> {
                    DailyTradingInformation stock = newTradingInfo.get(i);
                    TradingInfoDto dto = baseInfoDtos.get(i);

                    return new Object[] {
                            stock.getDate(),
                            stock.getTddClsprc(),
                            stock.getCmpprevddPrc(),
                            stock.getFlucRt(),
                            stock.getTddOpnprc(),
                            stock.getTddHgprc(),
                            stock.getTddLwprc(),
                            stock.getAccTrdvol(),
                            stock.getAccTedval(),
                            dto.getIsuCd()
                    };
                })
                .toList();
        try {
            jdbcTemplate.batchUpdate(sql, batchArgs);
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }
}
