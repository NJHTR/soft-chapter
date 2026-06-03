package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.Comment;
import com.douyin.vo.CommentVO;

import java.util.List;

public interface CommentService extends IService<Comment> {

    List<CommentVO> getVideoComments(Long videoId);

    Comment addComment(Comment comment);
}
