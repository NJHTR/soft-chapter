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

import java.util.List;
import java.util.Map;
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
    public List<CommentVO> getVideoComments(Long videoId) {
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
        Map<Long, UserVO> userMap = users.stream()
                .collect(Collectors.toMap(User::getUid, UserVO::from));

        return comments.stream().map(c -> {
            CommentVO vo = new CommentVO();
            vo.setId(c.getId());
            vo.setVideoId(c.getVideoId());
            vo.setUserId(c.getUserId());
            vo.setContent(c.getContent());
            vo.setLikeCount(c.getLikeCount());
            vo.setReplyCount(c.getReplyCount());
            vo.setParentId(c.getParentId());
            vo.setReplyToUserId(c.getReplyToUserId());
            vo.setCreateTime(c.getCreateTime());
            vo.setUser(userMap.getOrDefault(c.getUserId(), null));
            return vo;
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
