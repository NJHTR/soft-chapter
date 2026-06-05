package com.douyin.service;

import com.douyin.entity.LoginHistory;
import com.douyin.mapper.LoginHistoryMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 登录设备/安全信息收集服务
 * 统一处理本应在 AuthController/EmailController 中重复的采集逻辑
 */
@Slf4j
@Service
public class LoginDeviceService {

    private final LoginHistoryMapper loginHistoryMapper;
    private final SystemNoticeService systemNoticeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LoginDeviceService(LoginHistoryMapper loginHistoryMapper,
                              SystemNoticeService systemNoticeService) {
        this.loginHistoryMapper = loginHistoryMapper;
        this.systemNoticeService = systemNoticeService;
    }

    // ==================== 对外入口 ====================

    /** 完整流程：收集设备信息 → 保存记录 → 发系统通知 */
    public void recordAndNotify(Long userId, String uniqueId, String email,
                                String loginMethod, HttpServletRequest req) {
        LoginHistory history = collect(userId, email, loginMethod, req);
        try {
            loginHistoryMapper.insert(history);
        } catch (Exception e) {
            log.warn("保存登录记录失败: {}", e.getMessage());
            // 不影响登录流程
        }
        sendLoginNotice(userId, uniqueId, email, loginMethod, history, req);
    }

    // ==================== 查询 ====================

    public List<LoginHistory> findByUserId(Long userId, int pageNo, int pageSize) {
        int offset = (pageNo - 1) * pageSize;
        return loginHistoryMapper.findByUserId(userId, offset, pageSize);
    }

    /** 判断是否为首次在该设备登录 */
    public boolean isNewDevice(Long userId, String fingerprint) {
        if (fingerprint == null) return false;
        return loginHistoryMapper.countByFingerprint(userId, fingerprint) == 0;
    }

    // ==================== 数据采集 ====================

    private LoginHistory collect(Long userId, String email, String loginMethod, HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        String ip = getClientIp(req);

        LoginHistory h = new LoginHistory();
        h.setUserId(userId);
        h.setEmail(email);
        h.setLoginMethod(loginMethod);
        h.setLoginStatus("success");
        h.setCreateTime(LocalDateTime.now());

        // IP & 归属地
        h.setIp(ip);
        parseLocation(ip, h);

        // UA 解析
        h.setUserAgentRaw(ua);
        parseUserAgent(ua, h);

        // 前端传来的设备信息 (X-Device-Info header)
        String deviceHeader = req.getHeader("X-Device-Info");
        parseDeviceInfo(deviceHeader, h);

        // 从 ServletRequest 属性获取 TLS 信息
        h.setConnectionType(getConnectionType(req));

        // 设备指纹
        h.setDeviceFingerprint(buildFingerprint(h));

        return h;
    }

    // ==================== IP 归属地 ====================

    private void parseLocation(String ip, LoginHistory h) {
        if (ip == null || ip.isEmpty()) return;
        if (ip.startsWith("127.") || ip.startsWith("192.168.") || ip.startsWith("10.")
                || ip.startsWith("172.16.") || ip.startsWith("0:")) {
            h.setCountry("本地开发环境");
            return;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://ip-api.com/json/" + ip + "?lang=zh-CN&fields=country,regionName,city,isp"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                h.setCountry(nullSafe(node, "country"));
                h.setRegion(nullSafe(node, "regionName"));
                h.setCity(nullSafe(node, "city"));
                h.setIsp(nullSafe(node, "isp"));
            }
        } catch (Exception e) {
            log.debug("IP归属地查询失败: {}", e.getMessage());
        }
    }

    // ==================== UA 解析 ====================

    private void parseUserAgent(String ua, LoginHistory h) {
        if (ua == null || ua.isEmpty()) return;

        // OS
        if (ua.contains("iPhone")) {
            h.setDeviceOs("iPhone");
            h.setOsVersion(extractRegex(ua, "OS (\\d+[_\\d]*)").replace('_', '.'));
            h.setPlatformType("iPhone");
        } else if (ua.contains("iPad")) {
            h.setDeviceOs("iPad");
            h.setOsVersion(extractRegex(ua, "OS (\\d+[_\\d]*)").replace('_', '.'));
            h.setPlatformType("iPad");
        } else if (ua.contains("Android")) {
            h.setDeviceOs("Android");
            h.setOsVersion(extractRegex(ua, "Android (\\d+[\\.\\d]*)"));
            h.setPlatformType("Linux");
        } else if (ua.contains("Windows NT")) {
            h.setDeviceOs("Windows");
            String ver = extractRegex(ua, "Windows NT (\\d+[\\.\\d]*)");
            h.setOsVersion(ver.startsWith("10.0") ? "10/11" : ver);
            h.setPlatformType("Win32");
        } else if (ua.contains("Mac OS X") || ua.contains("Macintosh")) {
            h.setDeviceOs("Mac");
            h.setOsVersion(extractRegex(ua, "Mac OS X ([\\d_]+)").replace('_', '.'));
            h.setPlatformType("MacIntel");
        } else if (ua.contains("Linux")) {
            h.setDeviceOs("Linux");
            h.setPlatformType("Linux");
        } else {
            h.setDeviceOs("未知系统");
        }

        // 浏览器
        if (ua.contains("Edg/")) {
            h.setBrowserName("Edge");
            h.setBrowserVersion(extractRegex(ua, "Edg/(\\d+[\\.\\d]*)"));
        } else if (ua.contains("Chrome/") && !ua.contains("Edg/")) {
            h.setBrowserName("Chrome");
            h.setBrowserVersion(extractRegex(ua, "Chrome/(\\d+[\\.\\d]*)"));
        } else if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            h.setBrowserName("Safari");
            h.setBrowserVersion(extractRegex(ua, "Version/(\\d+[\\.\\d]*)"));
        } else if (ua.contains("Firefox/")) {
            h.setBrowserName("Firefox");
            h.setBrowserVersion(extractRegex(ua, "Firefox/(\\d+[\\.\\d]*)"));
        } else if (ua.contains("MicroMessenger")) {
            h.setBrowserName("微信内置浏览器");
        } else if (ua.contains("DingTalk")) {
            h.setBrowserName("钉钉内置浏览器");
        } else {
            h.setBrowserName("未知浏览器");
        }
    }

    // ==================== 前端设备信息解析 ====================

    private void parseDeviceInfo(String header, LoginHistory h) {
        if (header == null || header.isEmpty()) return;
        try {
            String json = java.net.URLDecoder.decode(header, "UTF-8");
            JsonNode d = objectMapper.readTree(json);

            if (d.has("screen")) {
                String[] parts = d.get("screen").asText().split("x");
                if (parts.length == 2) {
                    try {
                        h.setScreenWidth(Integer.parseInt(parts[0]));
                        h.setScreenHeight(Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (d.has("language")) h.setBrowserLanguage(d.get("language").asText());
            if (d.has("platform") && h.getPlatformType() == null)
                h.setPlatformType(d.get("platform").asText());
            if (d.has("cores")) h.setCpuCores(d.get("cores").asInt());
            if (d.has("memory")) h.setDeviceMemoryGb(d.get("memory").asInt());
            if (d.has("colorDepth")) h.setColorDepth(d.get("colorDepth").asInt());
            if (d.has("pixelRatio")) h.setPixelRatio(d.get("pixelRatio").asDouble());
            if (d.has("gpu")) h.setGpuRenderer(d.get("gpu").asText());
            if (d.has("connection")) h.setConnectionType(d.get("connection").asText());
            if (d.has("touchPoints")) h.setTouchSupport(d.get("touchPoints").asInt() > 0 ? 1 : 0);

        } catch (Exception e) {
            log.debug("解析前端设备信息失败: {}", e.getMessage());
        }
    }

    // ==================== 设备指纹 ====================

    private String buildFingerprint(LoginHistory h) {
        StringBuilder sb = new StringBuilder();
        sb.append(h.getDeviceOs() != null ? h.getDeviceOs() : "").append("|");
        sb.append(h.getOsVersion() != null ? h.getOsVersion() : "").append("|");
        sb.append(h.getBrowserName() != null ? h.getBrowserName() : "").append("|");
        sb.append(h.getScreenWidth() != null ? h.getScreenWidth() : "").append("x");
        sb.append(h.getScreenHeight() != null ? h.getScreenHeight() : "").append("|");
        sb.append(h.getCpuCores() != null ? h.getCpuCores() : "").append("|");
        sb.append(h.getDeviceMemoryGb() != null ? h.getDeviceMemoryGb() : "").append("|");
        sb.append(h.getPlatformType() != null ? h.getPlatformType() : "");
        // 简单hash
        String raw = sb.toString();
        return Integer.toHexString(raw.hashCode());
    }

    // ==================== 登录通知 ====================

    private void sendLoginNotice(Long userId, String uniqueId, String email,
                                 String loginMethod, LoginHistory h, HttpServletRequest req) {
        try {
            String timeStr = h.getCreateTime() != null
                    ? h.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : "";
            String osStr = (h.getDeviceOs() != null ? h.getDeviceOs() : "未知")
                    + (h.getOsVersion() != null && !h.getOsVersion().isEmpty() ? " " + h.getOsVersion() : "");
            String browserStr = (h.getBrowserName() != null ? h.getBrowserName() : "未知")
                    + (h.getBrowserVersion() != null && !h.getBrowserVersion().isEmpty() ? " " + h.getBrowserVersion() : "");
            String locStr = buildLocationStr(h);

            StringBuilder content = new StringBuilder();
            content.append("您的账号 ").append(uniqueId).append(" 于 ").append(timeStr).append(" 进行了登录操作。\n\n");
            content.append("登录方式：").append(formatLoginMethod(loginMethod, email)).append("\n");
            content.append("登录设备：").append(osStr).append(" / ").append(browserStr).append("\n");

            if (h.getScreenWidth() != null) {
                content.append("屏幕分辨率：").append(h.getScreenWidth()).append("x").append(h.getScreenHeight())
                        .append(h.getPixelRatio() != null ? " @" + h.getPixelRatio() + "x" : "").append("\n");
            }
            if (h.getCpuCores() != null) {
                content.append("CPU核心：").append(h.getCpuCores()).append(" 核");
                if (h.getDeviceMemoryGb() != null) content.append("  /  内存：").append(h.getDeviceMemoryGb()).append(" GB");
                content.append("\n");
            }
            if (h.getGpuRenderer() != null && !h.getGpuRenderer().isEmpty()) {
                content.append("GPU：").append(h.getGpuRenderer()).append("\n");
            }

            content.append("登录 IP：").append(h.getIp() != null ? h.getIp() : "未知").append("\n");
            content.append("登录地点：").append(!locStr.isEmpty() ? locStr : "未知").append("\n");

            if (h.getBrowserLanguage() != null) {
                content.append("浏览器语言：").append(h.getBrowserLanguage()).append("\n");
            }

            content.append("\n如非本人操作，建议立即修改密码，或在「设置 → 账号与安全 → 登录设备管理」中删除异常设备。");

            systemNoticeService.send(userId, "login", "账号登录提醒", content.toString());
        } catch (Exception e) {
            log.warn("发送登录通知失败: {}", e.getMessage());
        }
    }

    private String buildLocationStr(LoginHistory h) {
        StringBuilder loc = new StringBuilder();
        if (h.getCountry() != null && !h.getCountry().isEmpty()) loc.append(h.getCountry()).append(" ");
        if (h.getRegion() != null && !h.getRegion().isEmpty()) loc.append(h.getRegion()).append(" ");
        if (h.getCity() != null && !h.getCity().isEmpty()) loc.append(h.getCity());
        if (h.getIsp() != null && !h.getIsp().isEmpty()) loc.append("（").append(h.getIsp()).append("）");
        return loc.toString().trim();
    }

    private String formatLoginMethod(String method, String email) {
        if ("code".equals(method)) return "邮箱验证码登录（" + email + "）";
        return "邮箱密码登录（" + email + "）";
    }

    // ==================== 工具方法 ====================

    private String extractRegex(String input, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(input);
        return m.find() ? m.group(1) : "";
    }

    private String nullSafe(JsonNode node, String field) {
        return (node.has(field) && !node.get(field).isNull()) ? node.get(field).asText() : null;
    }

    private String getConnectionType(HttpServletRequest req) {
        // 从 TLS 属性推断
        try {
            Object cipher = req.getAttribute("jakarta.servlet.request.cipher_suite");
            if (cipher != null) return "https";
        } catch (Exception ignored) {}
        return req.isSecure() ? "https" : "http";
    }

    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            if (idx > 0) ip = ip.substring(0, idx).trim();
            return ip;
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) return ip;
        ip = req.getHeader("Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) return ip;
        ip = req.getHeader("WL-Proxy-Client-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) return ip;
        return req.getRemoteAddr();
    }
}
