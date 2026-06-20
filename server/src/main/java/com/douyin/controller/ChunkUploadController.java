package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.config.MinioConfig;
import io.minio.*;
import io.minio.errors.MinioException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.security.GeneralSecurityException;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/upload/chunk")
@ConditionalOnProperty(name = "minio.enabled", havingValue = "true")
public class ChunkUploadController {

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    public ChunkUploadController(MinioClient minioClient, MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
    }

    /**
     * 上传单个分片
     * POST /api/upload/chunk
     * FormData: file, uploadId, chunkIndex, totalChunks, fileName
     * MinIO 存储路径: chunks/{uploadId}/{chunkIndex:05d}
     */
    @PostMapping
    public Result<Map<String, Object>> uploadChunk(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkIndex") int chunkIndex,
            @RequestParam("totalChunks") int totalChunks,
            @RequestParam("fileName") String fileName) {

        if (uploadId == null || uploadId.length() < 8 || uploadId.length() > 128) {
            return Result.fail("无效的 uploadId");
        }
        if (chunkIndex < 0 || chunkIndex >= totalChunks) {
            return Result.fail("chunkIndex 越界");
        }

        String bucket = minioConfig.getBucketVideo();
        String chunkKey = String.format("chunks/%s/%05d", uploadId, chunkIndex);

        try {
            ensureBucket(bucket);

            // 检查分片是否已存在 (幂等)
            try {
                minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(chunkKey).build());
                return Result.ok(Map.of(
                        "uploadId", uploadId,
                        "chunkIndex", chunkIndex,
                        "status", "skipped",
                        "received", totalChunks > 0 ? (chunkIndex + 1) + " / " + totalChunks : "unknown"
                ));
            } catch (Exception ignored) {
                // 不存在, 继续上传
            }

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(chunkKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType("application/octet-stream")
                            .build()
            );

            log.info("分片上传完成: {} chunk {}/{} size={}KB",
                    uploadId, chunkIndex, totalChunks - 1, file.getSize() / 1024);

            return Result.ok(Map.of(
                    "uploadId", uploadId,
                    "chunkIndex", chunkIndex,
                    "status", "ok",
                    "received", (chunkIndex + 1) + " / " + totalChunks
            ));

        } catch (Exception e) {
            log.error("分片上传失败: uploadId={} index={}", uploadId, chunkIndex, e);
            return Result.fail("分片上传失败: " + e.getMessage());
        }
    }

    /**
     * 合并所有分片为最终文件
     * POST /api/upload/chunk/merge
     * Body JSON: { uploadId, fileName, totalChunks }
     * 返回: { url: "douyin-video/uuid.ext" }
     */
    @PostMapping("/merge")
    public Result<Map<String, Object>> mergeChunks(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String uploadId = (String) body.get("uploadId");
        String fileName = (String) body.get("fileName");
        Integer totalChunks = body.get("totalChunks") instanceof Integer
                ? (Integer) body.get("totalChunks") : null;

        if (uploadId == null || uploadId.length() < 8) {
            return Result.fail("无效的 uploadId");
        }
        if (totalChunks == null || totalChunks <= 0 || totalChunks > 2000) {
            return Result.fail("totalChunks 超出范围");
        }

        String bucket = minioConfig.getBucketVideo();
        String ext = extractExt(fileName);
        String finalObjectName = UUID.randomUUID().toString() + ext;
        String finalPath = bucket + "/" + finalObjectName;

        try {
            // 验证所有分片存在
            int found = 0;
            for (int i = 0; i < totalChunks; i++) {
                String chunkKey = String.format("chunks/%s/%05d", uploadId, i);
                try {
                    minioClient.statObject(StatObjectArgs.builder().bucket(bucket).object(chunkKey).build());
                    found++;
                } catch (Exception e) {
                    log.error("分片缺失: {}", chunkKey);
                    return Result.fail(String.format("分片 %d/%d 缺失，请重新上传", i, totalChunks - 1));
                }
            }

            log.info("开始合并分片: uploadId={} fileName={} totalChunks={} found={}",
                    uploadId, fileName, totalChunks, found);

            // 顺序读取分片，流式写入最终文件
            // MinIO 没有 append API, 用 compose 合并 (最多合并 1000 个源对象)
            List<ComposeSource> sources = new ArrayList<>();
            for (int i = 0; i < totalChunks; i++) {
                String chunkKey = String.format("chunks/%s/%05d", uploadId, i);
                sources.add(ComposeSource.builder()
                        .bucket(bucket)
                        .object(chunkKey)
                        .build());
            }

            // MinIO compose 每次最多合并约 1000 个, 如果需要更多需要分批
            if (sources.size() <= 1000) {
                minioClient.composeObject(
                        ComposeObjectArgs.builder()
                                .bucket(bucket)
                                .object(finalObjectName)
                                .sources(sources)
                                .build()
                );
            } else {
                // 超大批次: 先合并到中间文件再最终合并
                return Result.fail("文件过大，分片数超出限制(" + totalChunks + " > 1000)");
            }

            // 清理分片
            for (int i = 0; i < totalChunks; i++) {
                String chunkKey = String.format("chunks/%s/%05d", uploadId, i);
                try {
                    minioClient.removeObject(
                            RemoveObjectArgs.builder().bucket(bucket).object(chunkKey).build()
                    );
                } catch (Exception ignored) {
                }
            }

            log.info("分片合并完成: {} -> {} ({} chunks)", uploadId, finalPath, totalChunks);

            return Result.ok(Map.of(
                    "url", finalPath,
                    "status", "merged"
            ));

        } catch (Exception e) {
            log.error("分片合并失败: uploadId={}", uploadId, e);
            return Result.fail("合并失败: " + e.getMessage());
        }
    }

    private void ensureBucket(String bucket) throws Exception {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            throw new Exception("创建 bucket 失败: " + bucket, e);
        }
    }

    private String extractExt(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot).toLowerCase() : "";
    }
}
