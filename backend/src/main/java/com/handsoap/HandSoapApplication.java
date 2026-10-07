package com.handsoap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 排程基礎設施（review M2）：孤兒圖檔掃描（OrphanFileCleanupTask）依賴 {@code @Scheduled}。
 * 未啟用本註解時 {@code @Scheduled} 方法根本不會被觸發 —— 掃描會靜默失效。
 */
@EnableScheduling
@SpringBootApplication
public class HandSoapApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandSoapApplication.class, args);
    }
}
