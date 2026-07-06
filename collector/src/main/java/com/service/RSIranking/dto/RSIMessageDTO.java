package com.service.RSIranking.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * RSI 계산 메시지 DTO.
 *
 * <p>Redis Stream을 통해 전달되는 RSI 계산 요청 메시지입니다.</p>
 *
 * @author RSIranking Team
 * @version 1.0
 */
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RSIMessageDTO implements Serializable {
    /** 종목 코드 */
    private String isu_cd;
    /** RSI 계산 대상 날짜 (yyyyMMdd) */
    private String targetDate;
    /** 시장 구분 (KOSPI/KOSDAQ) */
    private String mkt_nm;
    /** RSI 계산에 필요한 과거 14일 장 날짜 목록 */
    private List<LocalDate> marketDate;
}
