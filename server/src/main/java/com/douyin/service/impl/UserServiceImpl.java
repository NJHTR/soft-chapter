package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.Follow;
import com.douyin.entity.User;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.VisitorMapper;
import com.douyin.service.UserService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtil jwtUtil;
    private final FollowMapper followMapper;
    private final VideoMapper videoMapper;
    private final VisitorMapper visitorMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(JwtUtil jwtUtil, FollowMapper followMapper, VideoMapper videoMapper,
                           VisitorMapper visitorMapper) {
        this.jwtUtil = jwtUtil;
        this.followMapper = followMapper;
        this.videoMapper = videoMapper;
        this.visitorMapper = visitorMapper;
    }

    private static final String DEFAULT_AVATAR = "/images/default-avatar.svg";
    private static final String DEFAULT_COVER = "/images/default-cover.svg";

    @Override
    public User register(String email, String password, String nickname) {
        User exist = getByEmail(email);
        if (exist != null) {
            throw new RuntimeException("该邮箱已注册");
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : email.split("@")[0]);
        user.setUniqueId(String.valueOf(System.currentTimeMillis()).substring(5));
        user.setAvatar168Url(DEFAULT_AVATAR);
        user.setAvatar300Url(DEFAULT_AVATAR);
        user.setCoverUrl(DEFAULT_COVER);
        save(user);
        return user;
    }

    @Override
    public String login(String email, String password) {
        User user = getByEmail(email);
        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        return jwtUtil.generateToken(user.getUid());
    }

    @Override
    public User getByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    @Override
    public User registerByEmail(String email) {
        User user = new User();
        user.setEmail(email);
        user.setNickname(email.split("@")[0]);
        user.setUniqueId(String.valueOf(System.currentTimeMillis()).substring(5));
        user.setAvatar168Url(DEFAULT_AVATAR);
        user.setAvatar300Url(DEFAULT_AVATAR);
        user.setCoverUrl(DEFAULT_COVER);
        save(user);
        return user;
    }

    @Override
    public void updateProfile(Long uid, String nickname, String signature, Integer gender, String birthday, String province, String city) {
        User user = getById(uid);
        if (user == null) throw new RuntimeException("用户不存在");
        if (nickname != null) user.setNickname(nickname);
        if (signature != null) user.setSignature(signature);
        if (gender != null) user.setGender(gender);
        if (birthday != null) user.setBirthday(birthday);
        if (province != null) user.setProvince(province);
        if (city != null) user.setCity(city);
        updateById(user);
    }

    @Override
    public void updateAvatar(Long uid, String avatarUrl) {
        User user = getById(uid);
        if (user == null) throw new RuntimeException("用户不存在");
        user.setAvatar168Url(avatarUrl);
        user.setAvatar300Url(avatarUrl);
        updateById(user);
    }

    @Override
    public void updateCover(Long uid, String coverUrl) {
        User user = getById(uid);
        if (user == null) throw new RuntimeException("用户不存在");
        user.setCoverUrl(coverUrl);
        updateById(user);
    }

    @Override
    public Object getFriends() {
        List<User> users = baseMapper.selectRandomFriends();
        List<UserVO> userVOs = users.stream().map(UserVO::from).toList();
        return Map.of(
                "all", userVOs,
                "recent", userVOs.size() > 2 ? userVOs.subList(0, 2) : userVOs,
                "eachOther", userVOs.size() > 2 ? userVOs.subList(0, 2) : userVOs
        );
    }

    @Override
    public boolean toggleFollow(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new RuntimeException("不能关注自己");
        }
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowId, targetUserId);
        Follow exist = followMapper.selectOne(wrapper);
        if (exist != null) {
            followMapper.deleteById(exist.getId());
            updateCount(userId, true, -1);       // 自己的关注数-1
            updateCount(targetUserId, false, -1); // 对方的粉丝数-1
            return false;
        }
        Follow f = new Follow();
        f.setUserId(userId);
        f.setFollowId(targetUserId);
        followMapper.insert(f);
        updateCount(userId, true, 1);       // 自己的关注数+1
        updateCount(targetUserId, false, 1); // 对方的粉丝数+1
        return true;
    }

    @Override
    public List<UserVO> getFollowings(Long userId) {
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .orderByDesc(Follow::getCreateTime));
        if (follows.isEmpty()) return List.of();
        List<Long> followIds = follows.stream().map(Follow::getFollowId).toList();
        List<User> users = listByIds(followIds);
        Map<Long, UserVO> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUid, UserVO::from));
        return followIds.stream().map(userMap::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<UserVO> getFollowers(Long userId) {
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                .eq(Follow::getFollowId, userId)
                .orderByDesc(Follow::getCreateTime));
        if (follows.isEmpty()) return List.of();
        List<Long> followerIds = follows.stream().map(Follow::getUserId).toList();
        List<User> users = listByIds(followerIds);
        Map<Long, UserVO> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUid, UserVO::from));
        return followerIds.stream().map(userMap::get).filter(Objects::nonNull).toList();
    }

    @Override
    public List<UserVO> searchUsers(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();
        List<User> users = baseMapper.searchByKeyword(keyword.trim());
        return users.stream().map(UserVO::from).toList();
    }

    @Override
    public List<UserVO> getRecentAuthors(Long userId, int limit) {
        if (userId == null) return List.of();
        List<Long> authorIds = videoMapper.findRecentAuthorIds(userId, limit);
        if (authorIds.isEmpty()) return List.of();
        List<User> users = baseMapper.selectBatchIds(authorIds);
        // 保持原顺序
        return authorIds.stream()
                .map(id -> users.stream().filter(u -> u.getUid().equals(id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .map(UserVO::from)
                .toList();
    }

    @Override
    public void recordVisit(Long userId, Long visitorId) {
        if (userId == null || visitorId == null || userId.equals(visitorId)) return;
        try {
            com.douyin.entity.Visitor v = new com.douyin.entity.Visitor();
            v.setUserId(userId);
            v.setVisitorId(visitorId);
            visitorMapper.insert(v);
        } catch (Exception ignored) { /* 重复记录忽略 */ }
    }

    @Override
    public List<UserVO> getVisitors(Long userId, int limit) {
        if (userId == null) return List.of();
        List<java.util.Map<String, Object>> rows = visitorMapper.getRecentVisitors(userId, limit);
        if (rows.isEmpty()) return List.of();
        List<Long> visitorIds = rows.stream()
                .map(r -> (Long) r.get("visitor_id"))
                .distinct().toList();
        List<User> users = baseMapper.selectBatchIds(visitorIds);
        java.util.Map<Long, User> userMap = users.stream()
                .collect(java.util.stream.Collectors.toMap(User::getUid, u -> u));
        return rows.stream()
                .map(r -> {
                    Long vid = (Long) r.get("visitor_id");
                    User u = userMap.get(vid);
                    return u != null ? UserVO.from(u) : null;
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** @param isFollowing true=更新自己的关注数, false=更新对方的粉丝数 */
    private void updateCount(Long uid, boolean isFollowing, int delta) {
        if (uid == null) return;
        try {
            User user = getById(uid);
            if (user != null) {
                if (isFollowing) {
                    user.setFollowingCount(Math.max(0,
                            (user.getFollowingCount() != null ? user.getFollowingCount() : 0) + delta));
                } else {
                    user.setFollowerCount(Math.max(0,
                            (user.getFollowerCount() != null ? user.getFollowerCount() : 0) + delta));
                }
                updateById(user);
            }
        } catch (Exception e) {
            log.error("updateCount failed: uid={}, isFollowing={}, delta={}", uid, isFollowing, delta, e);
        }
    }
}
