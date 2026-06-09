package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.entity.User;
import com.douyin.entity.Music;
import com.douyin.entity.Video;
import com.douyin.mapper.MusicMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.service.SystemNoticeService;
import com.douyin.service.MusicService;
import com.douyin.service.VideoService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.VideoVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final VideoService videoService;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final MusicService musicService;
    private final MusicMapper musicMapper;
    private final SystemNoticeService systemNoticeService;

    public AdminController(VideoService videoService, MusicService musicService, MusicMapper musicMapper, JwtUtil jwtUtil,
                           UserMapper userMapper, SystemNoticeService systemNoticeService) {
        this.videoService = videoService;
        this.musicService = musicService;
        this.musicMapper = musicMapper;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.systemNoticeService = systemNoticeService;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    private User checkAdmin(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return null;
        User user = userMapper.selectById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) return null;
        return user;
    }

    @GetMapping("/videos/pending")
    public Result<PageDTO<VideoVO>> pendingVideos(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("无管理员权限");

        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, "PENDING")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = videoService.page(new Page<>(pageNo, pageSize), wrapper);

        List<VideoVO> voList = new ArrayList<>();
        for (Video v : page.getRecords()) {
            VideoVO vo = new VideoVO();
            vo.setAwemeId(v.getId());
            vo.setDesc(v.getDesc());
            vo.setType(v.getType());
            vo.setDuration((long) (v.getDuration() * 1000));
            vo.setStatus(v.getStatus());

            VideoVO.VideoInfo info = new VideoVO.VideoInfo();
            String cover = v.getCoverUrl() != null && !v.getCoverUrl().isEmpty()
                    ? v.getCoverUrl() : v.getVideoUrl();
            info.setCover(VideoVO.UrlList.of(cover));
            info.setPlayAddr(VideoVO.UrlList.of(v.getVideoUrl()));
            info.setPoster(cover);
            vo.setVideo(info);

            VideoVO.Statistics stats = new VideoVO.Statistics();
            stats.setDiggCount(v.getLikeCount() != null ? v.getLikeCount() : 0L);
            stats.setCommentCount(v.getCommentCount() != null ? v.getCommentCount() : 0L);
            stats.setShareCount(v.getShareCount() != null ? v.getShareCount() : 0L);
            stats.setCollectCount(v.getCollectCount() != null ? v.getCollectCount() : 0L);
            stats.setPlayCount(v.getPlayCount() != null ? v.getPlayCount() : 0L);
            vo.setStatistics(stats);

            VideoVO.Music music = new VideoVO.Music();
            music.setTitle(v.getMusicTitle() != null ? v.getMusicTitle() : "原创");
            vo.setMusic(music);

            if (v.getAuthorUserId() != null) {
                User author = userMapper.selectById(v.getAuthorUserId());
                if (author != null) {
                    vo.setAuthor(com.douyin.vo.UserVO.from(author));
                }
            }
            voList.add(vo);
        }
        return Result.ok(new PageDTO<>(page.getTotal(), pageNo, pageSize, voList));
    }

    @PostMapping("/videos/{id}/approve")
    public Result<?> approve(@PathVariable Long id, HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("无管理员权限");

        Video video = videoService.getById(id);
        if (video == null) return Result.fail("视频不存�?);
        if (!"PENDING".equals(video.getStatus())) return Result.fail("该视频不在待审核状�?);

        video.setStatus("APPROVED");
        video.setReviewedBy(admin.getUid());
        video.setReviewTime(LocalDateTime.now());
        video.setReviewComment("");
        videoService.updateById(video);

        log.info("Admin {} approved video {}", admin.getUid(), id);

        // 发送通知给作�?        sendReviewNotice(video, true, "");

        return Result.ok(Map.of("status", "APPROVED", "message", "审核通过"));
    }

    @PostMapping("/videos/{id}/reject")
    public Result<?> reject(@PathVariable Long id,
                            @RequestBody Map<String, String> body,
                            HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("无管理员权限");

        Video video = videoService.getById(id);
        if (video == null) return Result.fail("视频不存�?);
        if (!"PENDING".equals(video.getStatus())) return Result.fail("该视频不在待审核状�?);

        String reason = body.getOrDefault("reason", "内容不符合平台规�?);
        video.setStatus("REJECTED");
        video.setReviewComment(reason);
        video.setReviewedBy(admin.getUid());
        video.setReviewTime(LocalDateTime.now());
        videoService.updateById(video);

        log.info("Admin {} rejected video {}: {}", admin.getUid(), id, reason);

        // 发送通知给作�?        sendReviewNotice(video, false, reason);

        return Result.ok(Map.of("status", "REJECTED", "message", "已驳�?));
    }


    // ========== ������� ==========

    @GetMapping("/music/pending")
    public Result<PageDTO<Map<String, Object>>> pendingMusic(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize,
            HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("�޹���ԱȨ��");

        LambdaQueryWrapper<Music> wrapper = new LambdaQueryWrapper<Music>()
                .eq(Music::getStatus, "PENDING")
                .orderByDesc(Music::getCreateTime);
        IPage<Music> page = musicMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        List<Map<String, Object>> voList = new ArrayList<>();
        for (Music m : page.getRecords()) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getName());
            map.put("artist", m.getArtist());
            map.put("album", m.getAlbum());
            map.put("coverUrl", m.getCoverUrl());
            map.put("playUrl", m.getPlayUrl());
            map.put("duration", m.getDuration());
            map.put("source", m.getSource());
            map.put("lyric", m.getLyric());
            map.put("status", m.getStatus());
            map.put("createTime", m.getCreateTime());
            voList.add(map);
        }
        return Result.ok(new PageDTO<>(page.getTotal(), pageNo, pageSize, voList));
    }

    @PostMapping("/music/{id}/approve")
    public Result<?> approveMusic(@PathVariable Long id, HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("�޹���ԱȨ��");

        Music music = musicService.getById(id);
        if (music == null) return Result.fail("���ֲ�����");
        if (!"PENDING".equals(music.getStatus())) return Result.fail("�����ֲ��ڴ����״̬");

        music.setStatus("APPROVED");
        music.setReviewedBy(admin.getUid());
        music.setReviewTime(LocalDateTime.now());
        music.setReviewComment("");
        musicMapper.updateById(music);

        log.info("Admin {} approved music {}", admin.getUid(), id);

        try {
            systemNoticeService.send(null, "review_approved",
                    "������Ʒ���ͨ��",
                    "���ϴ������֡�" + (music.getName() != null ? music.getName() : "δ֪") + "����ͨ����ˣ����ѹ���������");
        } catch (Exception e) {
            log.error("Failed to send review notice for music {}", id, e);
        }

        return Result.ok(Map.of("status", "APPROVED", "message", "���ͨ��"));
    }

    @PostMapping("/music/{id}/reject")
    public Result<?> rejectMusic(@PathVariable Long id,
                                 @RequestBody Map<String, String> body,
                                 HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("�޹���ԱȨ��");

        Music music = musicService.getById(id);
        if (music == null) return Result.fail("���ֲ�����");
        if (!"PENDING".equals(music.getStatus())) return Result.fail("�����ֲ��ڴ����״̬");

        String reason = body.getOrDefault("reason", "���ݲ�����ƽ̨�淶");
        music.setStatus("REJECTED");
        music.setReviewComment(reason);
        music.setReviewedBy(admin.getUid());
        music.setReviewTime(LocalDateTime.now());
        musicMapper.updateById(music);

        log.info("Admin {} rejected music {}: {}", admin.getUid(), id, reason);

        try {
            systemNoticeService.send(null, "review_rejected",
                    "������Ʒδͨ�����",
                    "���ϴ������֡�" + (music.getName() != null ? music.getName() : "δ֪") + "��δͨ����ˡ�ԭ��" + reason);
        } catch (Exception e) {
            log.error("Failed to send review notice for music {}", id, e);
        }

        return Result.ok(Map.of("status", "REJECTED", "message", "�Ѳ���"));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> stats(HttpServletRequest req) {
        User admin = checkAdmin(req);
        if (admin == null) return Result.fail("无管理员权限");

        long pendingCount = videoService.count(new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, "PENDING"));
        long approvedCount = videoService.count(new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, "APPROVED"));
        long rejectedCount = videoService.count(new LambdaQueryWrapper<Video>()
                .eq(Video::getStatus, "REJECTED"));
        long totalUsers = userMapper.selectCount(null);
        long musicPendingCount = musicMapper.selectCount(new LambdaQueryWrapper<Music>()
                .eq(Music::getStatus, "PENDING"));
        long musicApprovedCount = musicMapper.selectCount(new LambdaQueryWrapper<Music>()
                .eq(Music::getStatus, "APPROVED"));
        long musicRejectedCount = musicMapper.selectCount(new LambdaQueryWrapper<Music>()
                .eq(Music::getStatus, "REJECTED"));

        return Result.ok(Map.of(
                "pendingCount", pendingCount,
                "approvedCount", approvedCount,
                "rejectedCount", rejectedCount,
                "musicPendingCount", musicPendingCount,
                "musicApprovedCount", musicApprovedCount,
                "musicRejectedCount", musicRejectedCount,
                "totalUsers", totalUsers
        ));
    }

    private void sendReviewNotice(Video video, boolean approved, String reason) {
        try {
            String typeLabel = "image".equals(video.getType()) ? "图片" :
                               "text".equals(video.getType()) ? "文字" : "视频";
            String desc = video.getDesc();
            if (desc == null || desc.isEmpty()) desc = "无描�?;

            if (approved) {
                systemNoticeService.send(video.getAuthorUserId(), "review_approved",
                        typeLabel + "作品审核通过",
                        "您的" + typeLabel + "作品�? + desc + "》已通过审核，现已公开发布�?);
            } else {
                systemNoticeService.send(video.getAuthorUserId(), "review_rejected",
                        typeLabel + "作品未通过审核",
                        "您的" + typeLabel + "作品�? + desc + "》未通过审核。原因：" + reason);
            }
        } catch (Exception e) {
            log.error("Failed to send review notice for video {}", video.getId(), e);
        }
    }
}