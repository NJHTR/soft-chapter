package com.douyin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.douyin.mapper")
@EnableScheduling
public class DouyinServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DouyinServerApplication.class, args);
    }
}
