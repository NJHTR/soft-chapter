package com.douyin.rtc.capacity;

import com.douyin.common.Result;
import com.douyin.rtc.observability.CapacityMetrics;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 容量观测与控制入口。/capacity 是控制面 API：不采集、不转发媒体字节。
 * 观测方（部署监控、压测 harness）通过 observe 写入 registry；
 * 缓存缺失/过期时 evaluate fail-closed。
 */
@RestController
@RequestMapping("/api/rtc/capacity")
public class CapacityController {

    private static final Logger log = LoggerFactory.getLogger(CapacityController.class);

    private final AdmissionService admissionService;
    private final CapacityObservationSink sink;
    private final CapacityMetrics metrics;
    private final JwtUtil jwtUtil;

    public CapacityController(AdmissionService admissionService,
                              CapacityObservationSink sink,
                              CapacityMetrics metrics,
                              JwtUtil jwtUtil) {
        this.admissionService = admissionService;
        this.sink = sink;
        this.metrics = metrics;
        this.jwtUtil = jwtUtil;
    }

    /** 标记仅允许控制面/部署信任方调用；登录即可读决策（不含容量原始值）。 */
    @GetMapping("/decision")
    public Result<Map<String, Object>> decision(@RequestParam String node,
                                                @RequestParam(defaultValue = "0") long epoch,
                                                @RequestParam(required = false) String call_id,
                                                @RequestParam(required = false) String request_id,
                                                HttpServletRequest req) {
        if (loginUserId(req) == null) {
            return Result.fail(401, "请先登录");
        }
        AdmissionDecision decision;
        try {
            decision = admissionService.evaluate(node, epoch, call_id, request_id);
        } catch (CapacityReservationStore.CapacityStoreUnavailable e) {
            log.warn("[CAPACITY] registry/store unavailable, fail-closed: {}", e.getMessage());
            decision = AdmissionDecision.reject(node, epoch,
                    new AdmissionReason("registry_unavailable", null));
        }
        metrics.recordAdmission(node, decision);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("node", decision.nodeId());
        out.put("epoch", decision.epoch());
        out.put("decision", decision.decision().name());
        out.put("reason", decision.reason().code());
        if (decision.reason().dimension() != null) {
            out.put("dimension", decision.reason().dimension());
        }
        out.put("admission_enabled", admissionService.properties().isAdmissionEnabled());
        return Result.ok(out);
    }

    /** 观测写入（部署监控/压测 harness 使用）；只接受低基数维度。 */
    @PostMapping("/observe")
    public Result<Map<String, Object>> observe(@RequestBody Map<String, Object> body,
                                               HttpServletRequest req) {
        if (loginUserId(req) == null) {
            return Result.fail(401, "请先登录");
        }
        String node = str(body, "node");
        long epoch = num(body, "epoch");
        String dimension = str(body, "dimension");
        double value = dbl(body, "value");
        CapacityDimension dim = resolve(dimension);
        if (node == null || node.isBlank() || dim == null || !(value > 0)) {
            return Result.fail(400, "node/dimension/value 非法或维度未知");
        }
        sink.record(node, epoch, dim, value);
        metrics.observeDimension(node, dim, value);
        return Result.ok(Map.of("recorded", true, "node", node, "dimension", dim.label()));
    }

    /** reserve + consume + absorb 生命周期入口（压测 harness/幂等重放）。 */
    @PostMapping("/reservation")
    public Result<Map<String, Object>> reservation(@RequestBody Map<String, Object> body) {
        String requestId = str(body, "request_id");
        String callId = str(body, "call_id");
        String node = str(body, "node");
        long epoch = num(body, "epoch");
        String action = str(body, "action");
        if (requestId == null || node == null || node.isBlank() || action == null) {
            return Result.fail(400, "request_id/node/action 必填");
        }
        try {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("request_id", requestId);
            out.put("call_id", callId);
            out.put("action", action);
            switch (action) {
                case "reserve" -> {
                    Map<CapacityDimension, Double> vector = vector(body);
                    CapacityReservation created = admissionService.reserve(
                            requestId, callId, node, epoch, vector, Instant.now());
                    out.put("status", created.status().name());
                    out.put("idempotent", !created.requestId().isEmpty());
                }
                case "consume" -> {
                    CapacityReservation consumed = admissionService.consume(requestId);
                    out.put("status", consumed == null ? "NOT_FOUND" : consumed.status().name());
                }
                case "absorb" -> {
                    long seq = (long) dbl(body, "seq");
                    CapacityReservation absorbed = admissionService.absorb(requestId, seq);
                    out.put("status", absorbed == null ? "NOT_FOUND" : absorbed.status().name());
                }
                case "release" -> {
                    CapacityReservation released = admissionService.release(requestId);
                    out.put("status", released == null ? "NOT_FOUND" : released.status().name());
                }
                case "expire_pending" -> {
                    int expired = admissionService.expirePending(Instant.now());
                    out.put("expired", expired);
                }
                default -> {
                    return Result.fail(400, "未知 action");
                }
            }
            return Result.ok(out);
        } catch (CapacityReservationStore.CapacityStoreUnavailable e) {
            log.warn("[CAPACITY] reservation store unavailable: {}", e.getMessage());
            return Result.fail(503, "容量登记服务不可用，新房已 fail-closed");
        }
    }

    private static Map<CapacityDimension, Double> vector(Map<String, Object> body) {
        Map<CapacityDimension, Double> vector = new LinkedHashMap<>();
        for (CapacityDimension dim : CapacityDimension.values()) {
            Object value = body.get("reserved_" + dim.label());
            if (value instanceof Number number && number.doubleValue() > 0) {
                vector.put(dim, number.doubleValue());
            }
        }
        return vector;
    }

    private static CapacityDimension resolve(String dimension) {
        if (dimension == null) return null;
        try {
            return CapacityDimension.valueOf(dimension.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String str(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static double dbl(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private static long num(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
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