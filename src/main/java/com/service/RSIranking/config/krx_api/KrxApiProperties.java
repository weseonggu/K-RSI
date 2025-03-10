package com.service.RSIranking.config.krx_api;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "api.krx")
@Getter
@Setter
public class KrxApiProperties {
    private String key;
    private String kospiInfoUrl;
    private String kosdaqInfoUrl;
}
