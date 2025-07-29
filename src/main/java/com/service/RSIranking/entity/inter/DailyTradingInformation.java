package com.service.RSIranking.entity.inter;

import java.time.LocalDate;

public interface DailyTradingInformation {

    LocalDate getDate();

    Double getRsi();

    Double getAvgClosingGain();

    Double getAvgClosingLoss();

    void updateRSIInfo(Double ag, Double al, Double rsi);

}
