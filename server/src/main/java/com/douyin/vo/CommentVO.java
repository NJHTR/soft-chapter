package com.douyin.vo;

import com.douyin.entity.Comment;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 前端兼容格式的评论 — 嵌入用户信息
 * 字段蛇形命名由全局 SNAKE_CASE 处理
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentVO extends Comment {
    private UserVO user;
}
