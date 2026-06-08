package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.User;

public interface UserService extends IService<User> {

    /** 邮箱密码注册 */
    User register(String email, String password, String nickname, String role, String shopName);

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

    /** 搜索用户（按昵称或抖音号） */
    java.util.List<com.douyin.vo.UserVO> searchUsers(String keyword);

    /** 最近常看：根据互动历史获取最近关注的作者列表 */
    java.util.List<com.douyin.vo.UserVO> getRecentAuthors(Long userId, int limit);

    /** 记录访客 */
    void recordVisit(Long userId, Long visitorId);

    /** 获取访客列表 */
    java.util.List<com.douyin.vo.UserVO> getVisitors(Long userId, int limit);

    /** 朋友列表 (互相关注) */
    java.util.List<com.douyin.vo.UserVO> getFriends(Long userId);

    /** 设置主页访客展示开关 */
    void setVisitorDisplay(Long userId, boolean enabled);

    /** 检查用户是否已设置密码 */
    boolean hasPassword(Long userId);

    /** 设置或修改密码 */
    void setPassword(Long userId, String password);
}
