package com.douyin.rtc.p2p;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.domain.CallSession;
import com.douyin.rtc.service.CallService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 适配 CallService 为 P2P 控制面提供只读快照:eligibility context 与成员列表。
 * recording/moderation/stage/group 标记当前 CallSession 无独立字段,默认 false;
 * ACL 有效性以调用方登录态 + CallService 成员校验为准(控制器层保证)。
 */
@Component
public class P2pCallContextPortImpl implements P2pCallContextPort {

    private final CallService callService;

    public P2pCallContextPortImpl(CallService callService) {
        this.callService = callService;
    }

    @Override
    public long topologyGeneration(String callId) {
        return 1L;
    }

    @Override
    public Map<String, String> eligibilityContext(String callId) {
        CallSession session = callService.getCall(callId);
        if (session == null) {
            throw new CallDomainException(CallErrorCode.SESSION_NOT_FOUND, "通话不存在:" + callId);
        }
        List<Long> members = participantIds(callId);
        return P2pPolicyRule.context(false, session.getScope() == null ? "unknown" : session.getScope(),
                members.size(), session.getState(),
                false, false, false, false, !members.isEmpty());
    }

    @Override
    public List<Long> participantIds(String callId) {
        return callService.participants(callId).stream()
                .map(p -> p.getUserId())
                .collect(Collectors.toList());
    }
}