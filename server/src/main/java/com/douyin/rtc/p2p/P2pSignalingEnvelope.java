package com.douyin.rtc.p2p;

import lombok.Data;

import java.util.List;

/**
 * 版本化 P2P 信令 envelope(v1,契约第 7 节)。
 * 独立 RTC signaling port 只承载 SDP/ICE 控制数据;原始 payload 不为持久化,
 * 审计只记元数据与 SHA-256 摘要。
 */
@Data
public class P2pSignalingEnvelope {

    /** 恒为 "v1" */
    private String version = "v1";

    private String callId;

    /** call_id + topology_generation 绑定双方 consent */
    private long topologyGeneration;

    private Long fromUserId;

    private Long toUserId;

    /** offer | answer | ice */
    private String kind;

    /** 同一 (callId, fromUserId) 内单调递增 */
    private long seq;

    /** 幂等键(重复 event_id 直接回退或返回首次结果) */
    private String eventId;

    /** envelope 创建时间(毫秒 epoch),用于 TTL 校验 */
    private long createdEpochMs;

    /** offer/answer:原始 SDP 文本;ice:候选 JSON 数组文本 */
    private String payload;

    /** ice 候选数量(按逗号/换行分隔计);offer/answer 为 0 */
    private int candidateCount;

    /** HMAC-SHA256(signingSecret, version|callId|generation|from|to|kind|seq|payload) hex */
    private String signature;
}