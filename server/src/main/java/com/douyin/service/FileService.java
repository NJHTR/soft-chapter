package com.douyin.service;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 文件上传服务 - MinIO 预留实现, 配置 minio.enabled=true 后生效
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class FileService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private com.douyin.config.MinioConfig minioConfig;

    /** 上传视频 — 保留原始 Content-Type, 避免 webm→mp4 误标导致音轨丢失 */
    public String uploadVideo(MultipartFile file) throws Exception {
        String contentType = sanitizeContentType(file.getContentType(), "video/mp4");
        return upload(file, minioConfig.getBucketVideo(), contentType);
    }

    /** 上传图片 — 保留原始 Content-Type */
    public String uploadImage(MultipartFile file) throws Exception {
        String contentType = sanitizeContentType(file.getContentType(), "image/jpeg");
        return upload(file, minioConfig.getBucketImage(), contentType);
    }

    /** 剥离 codecs 参数, 只保留基础 MIME type (video/webm;codecs=vp8 → video/webm) */
    private String sanitizeContentType(String raw, String fallback) {
        if (raw == null || raw.trim().isEmpty()) return fallback;
        int semi = raw.indexOf(';');
        return (semi > 0 ? raw.substring(0, semi).trim() : raw.trim());
    }

    private String upload(MultipartFile file, String bucket, String contentType) throws Exception {
        // 确保 bucket 存在
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        String ext = getExtension(file.getOriginalFilename());
        String objectName = UUID.randomUUID() + ext;

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(contentType)
                        .build()
        );

        // 返回对象路径 (bucket/objectName)，由 FileController 流式返回文件内容
        return bucket + "/" + objectName;
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i == -1 ? "" : filename.substring(i);
    }

    /** 上传本地文件到 MinIO 并返回预签名 URL */
    public String uploadLocalFile(Path filePath, String bucket, String contentType) throws Exception {
        boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!found) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        String ext = getExtension(filePath.getFileName().toString());
        String objectName = UUID.randomUUID() + ext;

        try (InputStream is = Files.newInputStream(filePath)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(is, Files.size(filePath), -1)
                            .contentType(contentType)
                            .build()
            );
        }

        return bucket + "/" + objectName;
    }

    /** 从 MinIO 获取文件输入流，调用方负责关闭 */
    public InputStream getObject(String bucket, String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build()
        );
    }
}
