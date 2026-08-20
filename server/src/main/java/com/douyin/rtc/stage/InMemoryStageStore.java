package com.douyin.rtc.stage;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Map;

/**
 * 内存 Stage 存储。单实例部署语义正确(CAS + 审计);多实例需替换为 Redis/DB 端口,
 * 当前不作为生产扩容证据。
 */
public class InMemoryStageStore implements StageStore {

    private final Map<String, StageMember> members = new ConcurrentHashMap<>();
    private final Map<Long, List<StageAuditEntry>> auditLog = new ConcurrentHashMap<>();

    @Override
    public StageMember find(long liveId, long userId) {
        return members.getOrDefault(liveId + ":" + userId, StageMember.audience(liveId, userId));
    }

    @Override
    public boolean compareAndPut(StageMember expected, StageMember updated) {
        String key = updated.key();
        return members.replace(key, expected, updated)
                || (!members.containsKey(key) && members.putIfAbsent(key, updated) == null);
    }

    @Override
    public List<StageMember> members(long liveId) {
        return members.values().stream()
                .filter(m -> m.liveId() == liveId)
                .sorted((a, b) -> Long.compare(a.userId(), b.userId()))
                .toList();
    }

    @Override
    public void appendAudit(StageAuditEntry entry) {
        auditLog.computeIfAbsent(entry.liveId(), k -> new CopyOnWriteArrayList<>()).add(entry);
    }

    @Override
    public List<StageAuditEntry> audit(long liveId, int limit) {
        List<StageAuditEntry> list = auditLog.getOrDefault(liveId, List.of());
        return list.size() <= limit ? list : list.subList(list.size() - limit, list.size());
    }
}