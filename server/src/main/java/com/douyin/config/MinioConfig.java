package com.douyin.config;

import io.minio.MinioClient;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "minio")
public class MinioConfig {

    private boolean enabled;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketVideo;
    private String bucketImage;

    @Bean
    @ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
