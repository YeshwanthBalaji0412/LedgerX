package dev.ledgerx;

import org.springframework.boot.SpringApplication;

public class TestLedgerxApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerxApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
