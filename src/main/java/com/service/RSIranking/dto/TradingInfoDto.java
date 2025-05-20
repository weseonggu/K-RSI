package com.service.RSIranking.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KospiTradingInfoDto.class, name = "KOSPI"),
        @JsonSubTypes.Type(value = KosdaqTradingInfoDto.class, name = "KOSDAQ")
})
public interface TradingInfoDto {
}
