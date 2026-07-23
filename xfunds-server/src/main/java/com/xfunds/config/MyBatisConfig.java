package com.xfunds.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置类
 * 扫描 mapper 接口包
 */
@Configuration
@MapperScan("com.xfunds.mapper")
public class MyBatisConfig {
}
