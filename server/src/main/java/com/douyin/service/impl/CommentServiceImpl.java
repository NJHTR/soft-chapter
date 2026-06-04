package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.entity.Comment;
import com.douyin.entity.CommentLike;
import com.douyin.entity.User;
import com.douyin.entity.Video;
import com.douyin.mapper.CommentLikeMapper;
import com.douyin.mapper.CommentMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.service.CommentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private final UserMapper userMapper;
    private final VideoMapper videoMapper;
    private final CommentLikeMapper commentLikeMapper;

    public CommentServiceImpl(UserMapper userMapper, VideoMapper videoMapper, CommentLikeMapper commentLikeMapper) {
        this.userMapper = userMapper;
        this.videoMapper = videoMapper;
        this.commentLikeMapper = commentLikeMapper;
    }

    @Override
    public List<Map<String, Object>> getVideoComments(Long videoId, Long viewerUserId) {
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

        // 批量查询当前用户对这些评论的点赞状态
        Set<Long> likedCommentIds = Set.of();
        if (viewerUserId != null) {
            List<Long> commentIds = comments.stream().map(Comment::getId).toList();
            likedCommentIds = commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUserId, viewerUserId)
                            .in(CommentLike::getCommentId, commentIds))
                    .stream().map(CommentLike::getCommentId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalLiked = likedCommentIds;

        return comments.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            User u = userMap.get(c.getUserId());
            item.put("id", String.valueOf(c.getId()));
            item.put("comment_id", String.valueOf(c.getId()));
            item.put("user_id", String.valueOf(c.getUserId()));
            item.put("content", c.getContent());
            item.put("digg_count", c.getLikeCount() != null ? c.getLikeCount() : 0);
            item.put("user_digged", finalLiked.contains(c.getId()));
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
    public List<Map<String, Object>> getCommentReplies(Long commentId, Long viewerUserId) {
        List<Comment> replies = list(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getParentId, commentId)
                .orderByAsc(Comment::getCreateTime));

        if (replies.isEmpty()) return List.of();

        List<Long> replyUserIds = new java.util.ArrayList<>();
        for (Comment r : replies) {
            replyUserIds.add(r.getUserId());
            if (r.getReplyToUserId() != null && r.getReplyToUserId() != 0) {
                replyUserIds.add(r.getReplyToUserId());
            }
        }
        replyUserIds = replyUserIds.stream().distinct().toList();
        List<User> replyUsers = userMapper.selectBatchIds(replyUserIds);
        Map<Long, User> replyUserMap = replyUsers.stream()
                .collect(Collectors.toMap(User::getUid, ru -> ru));

        Set<Long> replyLikedIds = Set.of();
        if (viewerUserId != null) {
            List<Long> replyIds = replies.stream().map(Comment::getId).toList();
            replyLikedIds = commentLikeMapper.selectList(new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUserId, viewerUserId)
                            .in(CommentLike::getCommentId, replyIds))
                    .stream().map(CommentLike::getCommentId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalReplyLiked = replyLikedIds;

        return replies.stream().map(r -> {
            Map<String, Object> child = new LinkedHashMap<>();
            User ru = replyUserMap.get(r.getUserId());
            child.put("id", String.valueOf(r.getId()));
            child.put("comment_id", String.valueOf(r.getId()));
            child.put("user_id", String.valueOf(r.getUserId()));
            child.put("content", r.getContent());
            child.put("digg_count", r.getLikeCount() != null ? r.getLikeCount() : 0);
            child.put("user_digged", finalReplyLiked.contains(r.getId()));
            child.put("user_buried", false);
            child.put("sub_comment_count", 0);
            child.put("create_time", r.getCreateTime() != null
                    ? r.getCreateTime().atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond() * 1000 : 0);
            child.put("ip_location", "");
            child.put("nickname", ru != null && ru.getNickname() != null ? ru.getNickname() : "");
            child.put("avatar", ru != null && ru.getAvatar168Url() != null ? ru.getAvatar168Url() : "");
            child.put("reply_to_nickname", r.getReplyToUserId() != null && replyUserMap.containsKey(r.getReplyToUserId())
                    ? replyUserMap.get(r.getReplyToUserId()).getNickname() : "");
            return child;
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
        // 如果是回复, 更新父评论的回复数
        if (comment.getParentId() != null && comment.getParentId() != 0) {
            Comment parent = getById(comment.getParentId());
            if (parent != null) {
                parent.setReplyCount((parent.getReplyCount() != null ? parent.getReplyCount() : 0) + 1);
                updateById(parent);
            }
        }
        return comment;
    }

    @Override
    @Transactional
    public boolean toggleCommentLike(Long userId, Long commentId) {
        LambdaQueryWrapper<CommentLike> wrapper = new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getUserId, userId)
                .eq(CommentLike::getCommentId, commentId);
        CommentLike exist = commentLikeMapper.selectOne(wrapper);
        Comment comment = getById(commentId);
        if (comment == null) return false;
        if (exist != null) {
            commentLikeMapper.deleteById(exist.getId());
            comment.setLikeCount(Math.max(0, (comment.getLikeCount() != null ? comment.getLikeCount() : 0) - 1));
            updateById(comment);
            return false;
        }
        CommentLike like = new CommentLike();
        like.setUserId(userId);
        like.setCommentId(commentId);
        commentLikeMapper.insert(like);
        comment.setLikeCount((comment.getLikeCount() != null ? comment.getLikeCount() : 0) + 1);
        updateById(comment);
        return true;
    }
}
