package com.douyin.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * RTC 领域层仓储注册。现有 @MapperScan("com.douyin.mapper") 不覆盖
 * com.douyin.rtc 下的 mapper 接口,这里单独注册(仅新增文件,不改动既有配置)。
 * 注意:webhook ledger mapper 位于 com.douyin.rtc.webhook,必须一并扫描。
 */
@Configuration
@MapperScan({"com.douyin.rtc.repository", "com.douyin.rtc.webhook"})
public class RtcRepositoryConfig {
}