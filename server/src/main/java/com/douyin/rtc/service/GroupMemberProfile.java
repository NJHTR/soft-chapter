package com.douyin.rtc.service;

import lombok.Data;

/**
 * 群通话创建时的成员展示快照。
 *
 * <p>权限仍由 {@code t_group_member} 的 user_id 决定；这些字段只用于
 * 通话历史和房间 roster 展示，不能作为后续 ACL 来源。</p>
 */
@Data
public class GroupMemberProfile {

    private Long userId;
    private String nickname;
    private String avatar;

    public GroupMemberProfile() {
    }

    public GroupMemberProfile(Long userId, String nickname, String avatar) {
        this.userId = userId;
        this.nickname = nickname;
        this.avatar = avatar;
    }
}
