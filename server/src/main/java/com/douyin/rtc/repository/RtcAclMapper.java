package com.douyin.rtc.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 通话 ACL 查询 Mapper — 直接查询现有表(t_friend / t_group_member),
 * 不新建任何表。成员关系以服务端数据为准,不信任客户端 roster。
 */
@Mapper
public interface RtcAclMapper {

    /**
     * direct 通话要求互为好友: t_friend 双向记录且 status = 1。
     */
    @Select("SELECT COUNT(*) FROM t_friend f1 JOIN t_friend f2 "
            + "ON f1.user_id = f2.friend_id AND f1.friend_id = f2.user_id "
            + "WHERE f1.user_id = #{a} AND f1.friend_id = #{b} AND f1.status = 1 AND f2.status = 1")
    int mutualFriendConfirmed(@Param("a") Long a, @Param("b") Long b);

    @Select("SELECT COUNT(*) > 0 FROM t_group_member WHERE group_id = #{groupId} AND user_id = #{userId}")
    boolean isGroupMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    /** 群成员快照(不含客户端传入的 roster) */
    @Select("SELECT user_id FROM t_group_member WHERE group_id = #{groupId}")
    List<Long> listGroupMemberUserIds(@Param("groupId") Long groupId);
}