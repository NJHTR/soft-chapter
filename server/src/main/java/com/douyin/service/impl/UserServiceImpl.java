package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.Follow;
import com.douyin.entity.User;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.service.UserService;
import com.douyin.utils.JwtUtil;
import com.douyin.vo.UserVO;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtUtil jwtUtil;
    private final FollowMapper followMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserServiceImpl(JwtUtil jwtUtil, FollowMapper followMapper) {
        this.jwtUtil = jwtUtil;
        this.followMapper = followMapper;
    }

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
    @Transactional
    public boolean toggleFollow(Long userId, Long targetUserId) {
        LambdaQueryWrapper<Follow> wrapper = new LambdaQueryWrapper<Follow>()
                .eq(Follow::getUserId, userId)
                .eq(Follow::getFollowId, targetUserId);
        Follow exist = followMapper.selectOne(wrapper);
        if (exist != null) {
            followMapper.deleteById(exist.getId());
            updateFollowersCount(targetUserId, -1);
            return false;
        }
        Follow f = new Follow();
        f.setUserId(userId);
        f.setFollowId(targetUserId);
        followMapper.insert(f);
        updateFollowersCount(targetUserId, 1);
        return true;
    }

    private void updateFollowersCount(Long uid, int delta) {
        User user = getById(uid);
        if (user != null) {
            user.setFollowerCount(Math.max(0, (user.getFollowerCount() != null ? user.getFollowerCount() : 0) + delta));
            updateById(user);
        }
    }
}
