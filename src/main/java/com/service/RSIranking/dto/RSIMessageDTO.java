package com.service.RSIranking.dto;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RSIMessageDTO implements Serializable {
    private String isu_cd;
    private String targetDate;
    private String mkt_nm;
    private List<LocalDate> marketDate;
}
