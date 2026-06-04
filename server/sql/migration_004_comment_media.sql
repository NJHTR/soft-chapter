-- 评论表添加媒体附件字段
ALTER TABLE t_comment ADD COLUMN extra VARCHAR(2000) DEFAULT '' COMMENT '媒体附件JSON: [{type:image|voice, url, duration}]';
