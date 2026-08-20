package com.douyin.rtc.p2p;

import java.util.List;
import java.util.Map;

/**
 * 控制器 -> CallService 的只读适配端口:提供 eligibility 快照与成员列表。
 * 真实实现由 {@link P2pCallContextPort} 适配 CallService;测试用 mock。
 */
public interface P2pCallContextPort {

    /** 当前 call_id 的 topology_generation(无则 1) */
    long topologyGeneration(String callId);

    /** eligibility 快照(scope/state/人数/recording/moderation/stage/group/ACL) */
    Map<String, String> eligibilityContext(String callId);

    /** 两名成员 id(顺序无关) */
    List<Long> participantIds(String callId);
}