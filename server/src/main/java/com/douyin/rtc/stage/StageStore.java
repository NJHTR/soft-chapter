package com.douyin.rtc.stage;

import java.util.List;

/**
 * Stage 成员与审计存储端口(内存实现先行,MyBatis 持久化后续任务跟进)。
 */
public interface StageStore {

    /** 查找成员;不存在返回 {@link StageMember#audience}。 */
    StageMember find(long liveId, long userId);

    /** CAS 写入:仅当仓库中仍为 expected 时覆盖,返回是否成功。并发下失败者由调用方重试/拒绝。 */
    boolean compareAndPut(StageMember expected, StageMember updated);

    List<StageMember> members(long liveId);

    void appendAudit(StageAuditEntry entry);

    List<StageAuditEntry> audit(long liveId, int limit);
}