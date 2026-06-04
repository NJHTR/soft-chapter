package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.User;

public interface UserService extends IService<User> {

    /** 邮箱密码注册 */
    User register(String email, String password, String nickname);

    /** 邮箱密码登录 */
    String login(String email, String password);

    /** 通过邮箱查用户 */
    User getByEmail(String email);

    /** 邮箱快捷注册（无密码，用于验证码登录） */
    User registerByEmail(String email);

    /** 获取好友列表 */
    Object getFriends();

    /** 更新用户资料 */
    void updateProfile(Long uid, String nickname, String signature, Integer gender, String birthday, String province, String city);

    /** 更新头像 */
    void updateAvatar(Long uid, String avatarUrl);

    /** 更新背景图 */
    void updateCover(Long uid, String coverUrl);

    /** 切换关注状态, 返回: true=已关注 false=已取消 */
    boolean toggleFollow(Long userId, Long targetUserId);

    /** 获取关注列表 */
    java.util.List<com.douyin.vo.UserVO> getFollowings(Long userId);

    /** 获取粉丝列表 */
    java.util.List<com.douyin.vo.UserVO> getFollowers(Long userId);
}
