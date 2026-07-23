package com.xfunds;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 银行外汇交易系统启动类
 */
@SpringBootApplication
@MapperScan("com.xfunds.mapper")
@EnableScheduling
public class XfundsApplication {

    /**
     * 应用程序入口方法
     */
    public static void main(String[] args) {
        SpringApplication.run(XfundsApplication.class, args);
    }
}
