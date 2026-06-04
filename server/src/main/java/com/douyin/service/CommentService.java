package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.Comment;

import java.util.List;
import java.util.Map;

public interface CommentService extends IService<Comment> {

    List<Map<String, Object>> getVideoComments(Long videoId, Long viewerUserId);

    Comment addComment(Comment comment);

    boolean toggleCommentLike(Long userId, Long commentId);

    List<Map<String, Object>> getCommentReplies(Long commentId, Long viewerUserId);
}
