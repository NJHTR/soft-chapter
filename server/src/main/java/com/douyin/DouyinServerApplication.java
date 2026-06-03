package com.douyin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.douyin.mapper")
public class DouyinServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DouyinServerApplication.class, args);
    }
}
