package com.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot Application for Wallet Transfer Service
 */
@SpringBootApplication
@EnableTransactionManagement
public class WalletTransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletTransferApplication.class, args);
    }
}

