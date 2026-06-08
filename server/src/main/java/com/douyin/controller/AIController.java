package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.AiChatMessage;
import com.douyin.mapper.AiChatMessageMapper;
import com.douyin.service.ShopMessageService;
import com.douyin.utils.JwtUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final AiChatMessageMapper aiChatMessageMapper;
    private final JwtUtil jwtUtil;
    private final ShopMessageService shopMessageService;

    public AIController(AiChatMessageMapper aiChatMessageMapper, JwtUtil jwtUtil,
                        ShopMessageService shopMessageService) {
        this.aiChatMessageMapper = aiChatMessageMapper;
        this.jwtUtil = jwtUtil;
        this.shopMessageService = shopMessageService;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 获取聊天历史 */
    @GetMapping("/history")
    public Result<List<Map<String, String>>> history(@RequestParam("product_id") Long productId) {
        List<AiChatMessage> msgs = aiChatMessageMapper.selectList(
                new LambdaQueryWrapper<AiChatMessage>()
                        .eq(AiChatMessage::getProductId, productId)
                        .orderByAsc(AiChatMessage::getCreateTime)
        );
        List<Map<String, String>> list = msgs.stream().map(m -> {
            Map<String, String> map = new HashMap<>();
            map.put("role", m.getRole());
            map.put("content", m.getContent());
            return map;
        }).collect(Collectors.toList());
        return Result.ok(list);
    }

    @PostMapping("/chat")
    public Result<Map<String, Object>> chat(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        String userMessage = (String) body.get("message");
        if (userMessage == null || userMessage.isEmpty()) {
            return Result.fail("消息不能为空");
        }

        Object pidObj = body.get("product_id");
        Long productId = null;
        if (pidObj instanceof Number) {
            productId = ((Number) pidObj).longValue();
        }

        if ("your-api-key-here".equals(apiKey) || apiKey == null || apiKey.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("reply", "感谢您的咨询！客服功能正在配置中，请稍后再来。您的问题：「" + userMessage + "」已收到，我们会尽快回复。");
            result.put("role", "assistant");
            return Result.ok(result);
        }

        try {
            // 保存用户消息到DB
            if (productId != null) {
                AiChatMessage userMsgEntity = new AiChatMessage();
                userMsgEntity.setProductId(productId);
                userMsgEntity.setUserId(userId);
                userMsgEntity.setRole("user");
                userMsgEntity.setContent(userMessage);
                userMsgEntity.setCreateTime(LocalDateTime.now());
                aiChatMessageMapper.insert(userMsgEntity);
            }

            // 从DB加载历史（如果前端没传history且productId有值）
            @SuppressWarnings("unchecked")
            List<Map<String, String>> history = (List<Map<String, String>>) body.get("history");
            if ((history == null || history.isEmpty()) && productId != null) {
                List<AiChatMessage> dbHistory = aiChatMessageMapper.selectList(
                        new LambdaQueryWrapper<AiChatMessage>()
                                .eq(AiChatMessage::getProductId, productId)
                                .orderByAsc(AiChatMessage::getCreateTime)
                );
                history = dbHistory.stream().map(m -> {
                    Map<String, String> map = new HashMap<>();
                    map.put("role", m.getRole());
                    map.put("content", m.getContent());
                    return map;
                }).collect(Collectors.toList());
            }

            List<Map<String, String>> messages = new ArrayList<>();

            // 系统提示
            Map<String, String> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            String productInfo = body.get("product_info") != null ? body.get("product_info").toString() : "";
            systemMsg.put("content", "你是SeekFlow商城的智能客服助手，请热情、专业地回答用户关于商品的问题。"
                    + (productInfo.isEmpty() ? "" : "当前商品信息：" + productInfo)
                    + "回答请简洁明了，不超过200字。");
            messages.add(systemMsg);

            // 历史消息
            if (history != null) {
                messages.addAll(history);
            }

            // 当前用户消息
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            // 调用 DeepSeek API
            Map<String, Object> reqBody = new HashMap<>();
            reqBody.put("model", "deepseek-chat");
            reqBody.put("messages", messages);
            reqBody.put("max_tokens", 500);
            reqBody.put("temperature", 0.7);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(reqBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    baseUrl + "/v1/chat/completions", request, Map.class);

            Map<String, Object> respBody = response.getBody();
            if (respBody != null && respBody.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) respBody.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
                    String reply = (String) msg.get("content");

                    // 保存AI回复到DB
                    if (productId != null) {
                        AiChatMessage assistantMsg = new AiChatMessage();
                        assistantMsg.setProductId(productId);
                        assistantMsg.setUserId(userId);
                        assistantMsg.setRole("assistant");
                        assistantMsg.setContent(reply);
                        assistantMsg.setCreateTime(LocalDateTime.now());
                        aiChatMessageMapper.insert(assistantMsg);

                        // 推送消息通知
                        if (userId != null) {
                            shopMessageService.create(userId, "客服回复",
                                    "智能客服已回复您的咨询", "service", productId);
                        }
                    }

                    Map<String, Object> result = new HashMap<>();
                    result.put("reply", reply);
                    result.put("role", "assistant");
                    return Result.ok(result);
                }
            }
            return Result.fail("AI 服务暂不可用");
        } catch (Exception e) {
            log.error("AI chat error", e);
            return Result.fail("AI 客服暂时不可用，请稍后重试");
        }
    }
}
