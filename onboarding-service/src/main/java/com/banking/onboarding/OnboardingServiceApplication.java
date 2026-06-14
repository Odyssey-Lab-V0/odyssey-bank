package com.banking.onboarding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OnboardingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OnboardingServiceApplication.class, args);
    }
}
