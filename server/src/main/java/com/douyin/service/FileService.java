package com.douyin.service;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传服务 - MinIO 预留实现, 配置 minio.enabled=true 后生效
 */
@Service
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class FileService {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private com.douyin.config.MinioConfig minioConfig;

    /** 上传视频 */
    public String uploadVideo(MultipartFile file) throws Exception {
        return upload(file, minioConfig.getBucketVideo(), "video/mp4");
    }

    /** 上传图片 */
    public String uploadImage(MultipartFile file) throws Exception {
        return upload(file, minioConfig.getBucketImage(), "image/jpeg");
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

        // 返回访问 URL (7天有效期)
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .method(Method.GET)
                        .expiry(7, TimeUnit.DAYS)
                        .build()
        );
    }

    private String getExtension(String filename) {
        if (filename == null) return "";
        int i = filename.lastIndexOf('.');
        return i == -1 ? "" : filename.substring(i);
    }
}
