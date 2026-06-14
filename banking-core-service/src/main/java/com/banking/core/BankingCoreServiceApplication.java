package com.banking.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BankingCoreServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(BankingCoreServiceApplication.class, args);
    }
}
