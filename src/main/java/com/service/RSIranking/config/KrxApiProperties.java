package com.service.RSIranking.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "api.krx")
@Getter
@Setter
public class KrxApiProperties {
    private String url;
    private String key;
}
