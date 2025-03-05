package com.service.RSIranking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RsIrankingApplication {

	public static void main(String[] args) {
		SpringApplication.run(RsIrankingApplication.class, args);
	}

}
