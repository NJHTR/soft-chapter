package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_ai_chat_message")
public class AiChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long userId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}
