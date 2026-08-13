package com.douyin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * RTC 领域层仓储注册。现有 @MapperScan("com.douyin.mapper") 不覆盖
 * com.douyin.rtc.repository,这里单独注册(仅新增文件,不改动既有配置)。
 */
@Configuration
@MapperScan("com.douyin.rtc.repository")
public class RtcRepositoryConfig {
}