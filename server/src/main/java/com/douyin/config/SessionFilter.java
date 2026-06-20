package com.douyin.config;

import com.douyin.service.SessionService;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * 会话过滤器: 校验每个 API 请求的 token 是否对应一个活跃会话。
 *
 * 被踢掉的设备 → token 仍在 JWT 有效期内，但 ActiveSession 已标记 is_active=0
 * → 缓存未命中 → 返回 401 → 前端跳转登录页。
 *
 * 白名单路径不校验 (登录/注册/公开接口)。
 */
@Component
@Order(1)
public class SessionFilter implements Filter {

    private static final Set<String> WHITELIST = Set.of(
            "/api/login", "/api/register", "/api/email",
            "/api/music", "/api/upload", "/api/file",
            "/ws", "/api/admin",
            "/api/session", "/api/search/suggestions"
    );

    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    public SessionFilter(SessionService sessionService, JwtUtil jwtUtil) {
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String path = req.getRequestURI();

        // 白名单放行
        for (String white : WHITELIST) {
            if (path.startsWith(white)) {
                chain.doFilter(request, response);
                return;
            }
        }

        // 提取 token
        String token = extractToken(req);
        if (token == null) {
            // 无 token → 放行 (很多接口允许未登录访问)
            chain.doFilter(request, response);
            return;
        }

        // 先校验 JWT 签名和过期
        if (!jwtUtil.validateToken(token)) {
            chain.doFilter(request, response);
            return;
        }

        // 校验活跃会话
        if (!sessionService.isTokenActive(token)) {
            resp.setStatus(401);
            resp.setContentType("application/json;charset=UTF-8");
            resp.getWriter().write("{\"code\":401,\"msg\":\"登录已失效，请重新登录\",\"data\":null}");
            return;
        }

        // 异步更新最后活跃时间
        sessionService.touchToken(token);

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        // WebSocket 通过 query param 传 token
        String queryToken = req.getParameter("token");
        if (queryToken != null && !queryToken.isEmpty()) {
            return queryToken;
        }
        return null;
    }
}
