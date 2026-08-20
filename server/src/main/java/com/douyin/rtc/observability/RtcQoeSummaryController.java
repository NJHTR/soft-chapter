package com.douyin.rtc.observability;

import com.douyin.common.Result;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 客户端 QoE 摘要入口。媒体字节/原始 RTP 不进入控制面；
 * 只接受 2～5 秒聚合摘要，且仅通话成员可上报/读取。
 */
@RestController
@RequestMapping("/api/rtc/qoe")
public class RtcQoeSummaryController {

    private final QoeSummaryService summaryService;
    private final JwtUtil jwtUtil;

    public RtcQoeSummaryController(QoeSummaryService summaryService, JwtUtil jwtUtil) {
        this.summaryService = summaryService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/summary")
    public Result<Map<String, Object>> report(@RequestBody Map<String, Object> body,
                                              HttpServletRequest req) {
        Long userId = loginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        try {
            @SuppressWarnings("unchecked")
            Map<String, Double> aggregation = (Map<String, Double>) body.get("aggregation");
            @SuppressWarnings("unchecked")
            List<String> participants = (List<String>) body.get("participant_ids");
            RtcQoeSummary summary = new RtcQoeSummary(
                    string(body, "trace_id"), string(body, "call_id"),
                    participants == null ? List.of() : participants,
                    string(body, "node_id"), string(body, "topology"),
                    Instant.now(), aggregation == null ? Map.of() : aggregation);
            QoeAggregateStore.CallQoe accepted = summaryService.report(summary, userId);
            return Result.ok(Map.of(
                    "accepted", true,
                    "samples", accepted == null ? 0 : accepted.samples(),
                    "participant_count", accepted == null ? 0 : accepted.participantCount()));
        } catch (Exception e) {
            return Result.fail(400, e.getMessage() == null ? "QoE 摘要非法" : e.getMessage());
        }
    }

    @GetMapping("/summary")
    public Result<Map<String, Object>> read(@RequestParam String call_id, HttpServletRequest req) {
        Long userId = loginUserId(req);
        if (userId == null) return Result.fail(401, "请先登录");
        try {
            return Result.ok(summaryService.summaryFor(userId, call_id));
        } catch (Exception e) {
            return Result.fail(400, e.getMessage() == null ? "QoE 摘要读取失败" : e.getMessage());
        }
    }

    private static String string(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long loginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                return jwtUtil.getUserIdFromToken(auth.substring(7));
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }
}