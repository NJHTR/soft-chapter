package com.douyin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.entity.LiveRoom;
import com.douyin.entity.User;
import com.douyin.mapper.UserMapper;
import com.douyin.service.LiveService;
import com.douyin.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/live")
public class LiveController {

    private final LiveService liveService;
    private final UserMapper userMapper;

    public LiveController(LiveService liveService, UserMapper userMapper) {
        this.liveService = liveService;
        this.userMapper = userMapper;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        try {
            Object uid = req.getAttribute("uid");
            if (uid != null) return Long.parseLong(uid.toString());
        } catch (Exception ignored) {}
        return 1L; // 临时: 未登录返回1
    }

    /** 创建直播间 */
    @PostMapping("/create")
    public Result<LiveRoom> create(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        String title = body.getOrDefault("title", "直播间");
        String coverUrl = body.getOrDefault("coverUrl", "");
        LiveRoom room = liveService.createRoom(userId, title, coverUrl);
        return Result.ok(room);
    }

    /** 开播 */
    @PostMapping("/{id}/start")
    public Result<LiveRoom> start(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        LiveRoom room = liveService.startLive(id, userId);
        if (room == null) return Result.fail("直播间不存在或无权限");
        return Result.ok(room);
    }

    /** 关播 */
    @PostMapping("/{id}/end")
    public Result<LiveRoom> end(@PathVariable Long id, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        LiveRoom room = liveService.endLive(id, userId);
        if (room == null) return Result.fail("直播间不存在或无权限");
        return Result.ok(room);
    }

    /** 直播间详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id, HttpServletRequest req) {
        LiveRoom room = liveService.getById(id);
        if (room == null) return Result.fail("直播间不存在");

        User host = userMapper.selectById(room.getHostUserId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", room.getId());
        data.put("hostUserId", room.getHostUserId());
        data.put("title", room.getTitle());
        data.put("coverUrl", room.getCoverUrl());
        data.put("status", room.getStatus());
        data.put("viewerCount", room.getViewerCount());
        data.put("totalViewers", room.getTotalViewers());
        data.put("likeCount", room.getLikeCount());
        data.put("streamUrl", room.getStreamUrl());
        data.put("playUrl", room.getPlayUrl());
        data.put("createTime", room.getCreateTime());
        if (host != null) {
            data.put("host", UserVO.from(host));
        }
        return Result.ok(data);
    }

    /** 正在直播的房间列表 */
    @GetMapping("/rooms")
    public Result<PageDTO<Map<String, Object>>> rooms(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {

        LambdaQueryWrapper<LiveRoom> wrapper = new LambdaQueryWrapper<LiveRoom>()
                .eq(LiveRoom::getStatus, "LIVE")
                .orderByDesc(LiveRoom::getViewerCount)
                .orderByDesc(LiveRoom::getCreateTime);

        IPage<LiveRoom> page = liveService.page(new Page<>(pageNo, pageSize), wrapper);

        List<Long> hostIds = page.getRecords().stream()
                .map(LiveRoom::getHostUserId).distinct().toList();
        Map<Long, User> userMap = new HashMap<>();
        if (!hostIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(hostIds).stream()
                    .collect(Collectors.toMap(User::getUid, u -> u));
        }

        Map<Long, User> finalUserMap = userMap;
        List<Map<String, Object>> list = page.getRecords().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("title", r.getTitle());
            m.put("coverUrl", r.getCoverUrl());
            m.put("status", r.getStatus());
            m.put("viewerCount", r.getViewerCount());
            m.put("likeCount", r.getLikeCount());
            User u = finalUserMap.get(r.getHostUserId());
            if (u != null) {
                m.put("host", UserVO.from(u));
            }
            return m;
        }).toList();

        return Result.ok(new PageDTO<>(page.getTotal(), pageNo, pageSize, list));
    }

    /** 加入直播间（增加人数） */
    @PostMapping("/{id}/join")
    public Result<?> join(@PathVariable Long id) {
        LiveRoom room = liveService.joinRoom(id);
        if (room == null) return Result.fail("直播间未开播或不存在");
        return Result.ok(Map.of("viewerCount", room.getViewerCount()));
    }

    /** 离开直播间 */
    @PostMapping("/{id}/leave")
    public Result<?> leave(@PathVariable Long id) {
        liveService.leaveRoom(id);
        return Result.ok();
    }

    /** 点赞直播间 */
    @PostMapping("/{id}/like")
    public Result<?> like(@PathVariable Long id) {
        liveService.addLike(id);
        LiveRoom room = liveService.getById(id);
        return Result.ok(Map.of("likeCount", room != null ? room.getLikeCount() : 0));
    }
}
