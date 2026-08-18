package dev.ledgerx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class LedgerxApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerxApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}