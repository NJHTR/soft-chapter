package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.Friend;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface FriendMapper extends BaseMapper<Friend> {

    /** 查询双方是否有互关关系 */
    @Select("SELECT COUNT(*) > 0 FROM t_follow a INNER JOIN t_follow b " +
            "ON a.user_id = b.follow_id AND a.follow_id = b.user_id " +
            "WHERE a.user_id = #{userId} AND a.follow_id = #{targetId}")
    boolean isMutualFollow(@Param("userId") Long userId, @Param("targetId") Long targetId);

    /** 查询某用户的朋友列表(已接受) */
    @Select("SELECT u.* FROM t_user u INNER JOIN t_friend f " +
            "ON (u.uid = f.friend_id AND f.user_id = #{userId}) " +
            "OR (u.uid = f.user_id AND f.friend_id = #{userId}) " +
            "WHERE f.status = 1")
    List<com.douyin.entity.User> findFriends(@Param("userId") Long userId);

    /** 查询待处理的朋友申请 */
    @Select("SELECT f.* FROM t_friend f WHERE f.friend_id = #{userId} AND f.status = 0")
    List<Friend> findPendingRequests(@Param("userId") Long userId);

    /** 查询我发起的待处理申请 */
    @Select("SELECT f.* FROM t_friend f WHERE f.user_id = #{userId} AND f.status = 0")
    List<Friend> findSentRequests(@Param("userId") Long userId);
}
