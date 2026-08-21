package com.douyin.rtc.service;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.repository.RtcAclMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通话成员 ACL — 校验发起方/目标是否具备通话资格。
 * 成员关系一律查询现有表(t_follow / t_group_member),不建新表,
 * 不信任客户端传入的昵称、空 user_id 或 roster 文本。
 */
@Service
public class AclService {

    private final RtcAclMapper aclMapper;

    public AclService(RtcAclMapper aclMapper) {
        this.aclMapper = aclMapper;
    }

    public void assertAuthenticated(Long actorId) {
        if (actorId == null) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "未登录,无权操作通话");
        }
    }

    /**
     * direct 通话: 要求双方互相关注(t_follow 双向记录)。
     */
    public void assertDirectCallAllowed(Long actorId, Long targetUserId) {
        assertAuthenticated(actorId);
        if (targetUserId == null || targetUserId <= 0) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少目标用户");
        }
        if (actorId.equals(targetUserId)) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "不能呼叫自己");
        }
        if (!aclMapper.userExists(targetUserId)) {
            throw new CallDomainException(CallErrorCode.USER_NOT_FOUND, "目标用户不存在");
        }
        if (aclMapper.mutualFollowConfirmed(actorId, targetUserId) < 1) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "双方未互相关注,无权发起通话");
        }
    }

    /**
     * group 通话: 要求发起者是群成员(t_group_member 存在)。
     */
    public void assertGroupCallAllowed(Long actorId, Long groupId) {
        assertAuthenticated(actorId);
        if (groupId == null || groupId <= 0) {
            throw new CallDomainException(CallErrorCode.INVALID_ARGUMENT, "缺少群组ID");
        }
        if (!isGroupMember(actorId, groupId)) {
            throw new CallDomainException(CallErrorCode.NOT_AUTHORIZED, "非群成员,无权发起群通话");
        }
    }

    public boolean isGroupMember(Long userId, Long groupId) {
        return aclMapper.isGroupMember(groupId, userId);
    }

    /** 群成员快照(服务端权威) */
    public List<Long> groupMembers(Long groupId) {
        return aclMapper.listGroupMemberUserIds(groupId);
    }

    /** 群成员展示快照；仅供创建通话时写入 profile_snapshot。 */
    public List<GroupMemberProfile> groupMemberProfiles(Long groupId) {
        return aclMapper.listGroupMemberProfiles(groupId);
    }
}
