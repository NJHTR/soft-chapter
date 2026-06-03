package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.Comment;

import java.util.List;
import java.util.Map;

public interface CommentService extends IService<Comment> {

    List<Map<String, Object>> getVideoComments(Long videoId);

    Comment addComment(Comment comment);
}
