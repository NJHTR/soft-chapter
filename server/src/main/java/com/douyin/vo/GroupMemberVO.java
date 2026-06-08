package com.douyin.vo;

import com.douyin.entity.GroupMember;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GroupMemberVO {

    @JsonProperty("group_id")
    private Long groupId;

    @JsonProperty("user_id")
    private Long userId;

    private Integer role;

    private String nickname;

    @JsonProperty("is_muted")
    private Integer isMuted;

    @JsonProperty("join_time")
    private LocalDateTime joinTime;

    /** 成员用户信息 */
    private UserVO user;

    public static GroupMemberVO from(GroupMember member, UserVO user) {
        GroupMemberVO vo = new GroupMemberVO();
        vo.groupId = member.getGroupId();
        vo.userId = member.getUserId();
        vo.role = member.getRole();
        vo.nickname = member.getNickname();
        vo.isMuted = member.getIsMuted();
        vo.joinTime = member.getJoinTime();
        vo.user = user;
        return vo;
    }
}
