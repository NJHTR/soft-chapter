package com.douyin.rtc.repository;

import com.douyin.rtc.service.GroupMemberProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通话 ACL 查询 Mapper — 直接查询现有表(t_follow / t_group_member),
 * 不新建任何表。成员关系以服务端数据为准,不信任客户端 roster。
 */
@Mapper
public interface RtcAclMapper {

    /**
     * direct 通话要求互相关注: t_follow 存在双向关注记录。
     */
    @Select("SELECT COUNT(*) FROM t_follow f1 JOIN t_follow f2 "
            + "ON f1.user_id = f2.follow_id AND f1.follow_id = f2.user_id "
            + "WHERE f1.user_id = #{a} AND f1.follow_id = #{b}")
    int mutualFollowConfirmed(@Param("a") Long a, @Param("b") Long b);

    @Select("SELECT COUNT(*) > 0 FROM t_group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    boolean isGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 群成员快照(不含客户端传入的 roster) */
    @Select("SELECT user_id FROM t_group_member WHERE group_id = #{groupId}")
    List<Long> listGroupMemberUserIds(@Param("groupId") Long groupId);

    /**
     * 群通话创建时读取展示快照。user_id 仍来自群成员表，昵称优先使用群内昵称，
     * 头像来自用户表；这些值只写入 rtc_call_participant.profile_snapshot。
     */
    @Select("SELECT gm.user_id AS user_id, "
            + "COALESCE(NULLIF(gm.nickname, ''), u.nickname) AS nickname, "
            + "COALESCE(NULLIF(u.avatar_168_url, ''), u.avatar_300_url, '') AS avatar "
            + "FROM t_group_member gm LEFT JOIN t_user u ON u.uid = gm.user_id "
            + "WHERE gm.group_id = #{groupId} ORDER BY gm.id ASC")
    List<GroupMemberProfile> listGroupMemberProfiles(@Param("groupId") Long groupId);
}
