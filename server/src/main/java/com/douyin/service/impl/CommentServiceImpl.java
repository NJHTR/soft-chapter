package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.Comment;
import com.douyin.entity.User;
import com.douyin.entity.Video;
import com.douyin.mapper.CommentMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.service.CommentService;
import com.douyin.vo.CommentVO;
import com.douyin.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final UserMapper userMapper;
    private final VideoMapper videoMapper;

    public CommentServiceImpl(UserMapper userMapper, VideoMapper videoMapper) {
        this.userMapper = userMapper;
        this.videoMapper = videoMapper;
    }

    @Override
    public List<Map<String, Object>> getVideoComments(Long videoId) {
        List<Comment> comments = list(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getVideoId, videoId)
                .eq(Comment::getParentId, 0L)
                .orderByDesc(Comment::getCreateTime));

        if (comments.isEmpty()) return List.of();

        List<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .distinct()
                .toList();
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getUid, u -> u));

        return comments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            User u = userMap.get(c.getUserId());
            item.put("id", String.valueOf(c.getId()));
            item.put("comment_id", String.valueOf(c.getId()));
            item.put("content", c.getContent());
            item.put("digg_count", c.getLikeCount() != null ? c.getLikeCount() : 0);
            item.put("user_digged", false);
            item.put("user_buried", false);
            item.put("sub_comment_count", c.getReplyCount() != null ? c.getReplyCount() : 0);
            item.put("create_time", c.getCreateTime() != null
                    ? c.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond() * 1000 : 0);
            item.put("ip_location", "");
            if (u != null) {
                item.put("nickname", u.getNickname() != null ? u.getNickname() : "");
                item.put("avatar", u.getAvatar168Url() != null ? u.getAvatar168Url() : "");
            } else {
                item.put("nickname", "");
                item.put("avatar", "");
            }
            item.put("showChildren", false);
            item.put("children", List.of());
            return item;
        }).toList();
    }

    @Override
    @Transactional
    public Comment addComment(Comment comment) {
        save(comment);
        // 更新视频评论数
        Video video = videoMapper.selectById(comment.getVideoId());
        if (video != null) {
            video.setCommentCount((video.getCommentCount() != null ? video.getCommentCount() : 0) + 1);
            videoMapper.updateById(video);
        }
        return comment;
    }
}
