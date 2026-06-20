package com.douyin.service;

import com.douyin.entity.ActiveSession;
import com.douyin.entity.LoginHistory;
import com.douyin.mapper.ActiveSessionMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 活跃会话管理: 创建 / 列表 / 单个撤销 / 全部撤销 / token 有效性校验
 *
 * 内存缓存 tokenHash → sessionId, 避免 Filter 每次查 DB。
 * 启动时从 DB 加载所有活跃会话, 登录/撤销时实时更新缓存。
 */
@Slf4j
@Service
public class SessionService {

    private final ActiveSessionMapper sessionMapper;

    /** tokenHash → sessionId (内存缓存, 仅存活跃的) */
    private final Map<String, Long> activeTokenCache = new ConcurrentHashMap<>();

    public SessionService(ActiveSessionMapper sessionMapper) {
        this.sessionMapper = sessionMapper;
    }

    @PostConstruct
    void warmUp() {
        try {
            List<ActiveSession> sessions = sessionMapper.findAllActive();
            for (ActiveSession s : sessions) {
                if (s.getTokenHash() != null) {
                    activeTokenCache.put(s.getTokenHash(), s.getId());
                }
            }
            log.info("活跃会话缓存预热完成: {} 个", activeTokenCache.size());
        } catch (Exception e) {
            log.warn("活跃会话缓存预热失败: {}", e.getMessage());
        }
    }

    // ==================== Token 校验 (Filter 用) ====================

    /** 校验 token 是否对应一个活跃会话。true = 有效, false = 已失效/不存在 */
    public boolean isTokenActive(String token) {
        String hash = sha256(token);
        if (activeTokenCache.containsKey(hash)) return true;
        // 缓存未命中 → 回查 DB (可能是其他实例创建的)
        try {
            ActiveSession session = sessionMapper.findByTokenHash(hash);
            if (session != null) {
                activeTokenCache.put(hash, session.getId());
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 更新最后活跃时间 (低频, 异步可接受) */
    public void touchToken(String token) {
        String hash = sha256(token);
        Long sessionId = activeTokenCache.get(hash);
        if (sessionId != null) {
            try {
                sessionMapper.touchLastActive(sessionId);
            } catch (Exception ignored) {}
        }
    }

    // ==================== 创建会话 ====================

    /** 登录成功后创建活跃会话 */
    public ActiveSession createSession(Long userId, String token, LoginHistory h) {
        ActiveSession s = new ActiveSession();
        s.setUserId(userId);
        s.setTokenHash(sha256(token));
        s.setDeviceFingerprint(h.getDeviceFingerprint());
        s.setDeviceName(buildDeviceName(h));
        s.setIp(h.getIp());
        s.setCity(buildCity(h));
        s.setBrowserName(h.getBrowserName());
        s.setBrowserVersion(h.getBrowserVersion());
        s.setDeviceOs(h.getDeviceOs());
        s.setOsVersion(h.getOsVersion());
        s.setScreenWidth(h.getScreenWidth());
        s.setScreenHeight(h.getScreenHeight());
        s.setCpuCores(h.getCpuCores());
        s.setDeviceMemoryGb(h.getDeviceMemoryGb());
        s.setGpuRenderer(h.getGpuRenderer());
        s.setLoginTime(LocalDateTime.now());
        s.setLastActiveTime(LocalDateTime.now());
        s.setIsActive(1);
        s.setCreateTime(LocalDateTime.now());

        sessionMapper.insert(s);

        // 加入缓存
        activeTokenCache.put(s.getTokenHash(), s.getId());

        log.info("会话创建: userId={} sessionId={} device={}", userId, s.getId(), s.getDeviceName());
        return s;
    }

    // ==================== 查询 ====================

    public List<ActiveSession> listUserSessions(Long userId) {
        return sessionMapper.findActiveByUserId(userId);
    }

    // ==================== 撤销 ====================

    /** 撤销单个会话 (踢掉某个设备) */
    public boolean revokeSession(Long sessionId, Long userId) {
        ActiveSession s = sessionMapper.selectById(sessionId);
        if (s == null || !s.getUserId().equals(userId)) return false;

        sessionMapper.revokeById(sessionId);
        if (s.getTokenHash() != null) {
            activeTokenCache.remove(s.getTokenHash());
        }
        log.info("会话撤销: userId={} sessionId={} device={}", userId, sessionId, s.getDeviceName());
        return true;
    }

    /** 撤销除当前外的所有会话 (一键登出其他设备) */
    public int revokeAllOtherSessions(Long userId, Long currentSessionId) {
        List<ActiveSession> sessions = sessionMapper.findActiveByUserId(userId);
        int count = 0;
        for (ActiveSession s : sessions) {
            if (s.getId().equals(currentSessionId)) continue;
            sessionMapper.revokeById(s.getId());
            if (s.getTokenHash() != null) {
                activeTokenCache.remove(s.getTokenHash());
            }
            count++;
        }
        log.info("批量撤销: userId={} 除{}外共{}个", userId, currentSessionId, count);
        return count;
    }

    /** 根据当前请求的 token 找到对应的 sessionId */
    public Long findSessionIdByToken(String token) {
        String hash = sha256(token);
        Long cached = activeTokenCache.get(hash);
        if (cached != null) return cached;
        try {
            ActiveSession s = sessionMapper.findByTokenHash(hash);
            if (s != null) {
                activeTokenCache.put(hash, s.getId());
                return s.getId();
            }
        } catch (Exception ignored) {}
        return null;
    }

    // ==================== 工具方法 ====================

    private String buildDeviceName(LoginHistory h) {
        StringBuilder sb = new StringBuilder();
        if (h.getBrowserName() != null) sb.append(h.getBrowserName());
        if (h.getDeviceOs() != null) {
            if (!sb.isEmpty()) sb.append(" on ");
            sb.append(h.getDeviceOs());
        }
        String city = buildCity(h);
        if (!city.isEmpty()) sb.append("（").append(city).append("）");
        return !sb.isEmpty() ? sb.toString() : "未知设备";
    }

    private String buildCity(LoginHistory h) {
        StringBuilder loc = new StringBuilder();
        if (h.getCity() != null && !h.getCity().isEmpty()) loc.append(h.getCity());
        else if (h.getRegion() != null && !h.getRegion().isEmpty()) loc.append(h.getRegion());
        else if (h.getCountry() != null && !h.getCountry().isEmpty()) loc.append(h.getCountry());
        return loc.toString();
    }

    static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
