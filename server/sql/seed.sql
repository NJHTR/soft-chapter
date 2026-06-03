-- =========================================
-- 抖音种子数据 - 在 Navicat 中执行
-- 先跑 init.sql 建表, 再跑 migrate_add_email.sql 加字段, 最后跑这个
-- =========================================

USE douyin;

-- 先清掉旧数据
DELETE FROM t_comment;
DELETE FROM t_video;
DELETE FROM t_user;

-- 测试用户 (邮箱登录, phone 填不同占位值避开唯一约束)
INSERT INTO t_user (uid, unique_id, nickname, signature, gender, province, city,
    avatar_168_url, avatar_300_url, cover_url, follower_count, following_count, total_favorited, video_count, phone, email, password)
VALUES
(1, '12345xiaolaohu', '杨老虎', '磕穿下巴掉牙版 | 专注原创搞笑视频', 2, '广东', '深圳',
 '', '', '',
 1280000, 126, 89000000, 56, '10000000001', 'seed1@qq.com', ''),
(2, '68310389333', '我是香秀', '每天更新爆笑段子 | 感谢关注', 2, '浙江', '杭州',
 '', '', '',
 2560000, 88, 120000000, 89, '10000000002', 'seed2@qq.com', ''),
(3, '59054327754', '条子', '搞笑我们是认真的', 1, '四川', '成都',
 '', '', '',
 980000, 200, 45000000, 34, '10000000003', 'seed3@qq.com', '');

-- 推荐视频 (使用公开测试视频)
INSERT INTO t_video (id, video_url, cover_url, `desc`, duration, width, height, author_user_id, music_title, type, like_count, comment_count, share_count)
VALUES
(1001, 'https://dy.ttentau.top/0.mp4', '', '这个反转也太搞笑了吧 #搞笑 #反转', 15.0, 1080, 1920, 1, '杨老虎原创BGM', 'recommend-video', 520000, 18000, 12000),
(1002, 'https://dy.ttentau.top/0.mp4', '', '今天给大家整个活 #才艺 #整活', 18.5, 1080, 1920, 1, '@抖音热门BGM', 'recommend-video', 890000, 35000, 28000),
(1003, 'https://dy.ttentau.top/0.mp4', '', '上班第一天vs上班一年的区别 #职场 #搞笑', 22.0, 1080, 1920, 2, '@香秀专属BGM', 'recommend-video', 1200000, 56000, 45000),
(1004, 'https://dy.ttentau.top/0.mp4', '', '千万不要让男朋友带娃 #带娃 #搞笑日常', 12.0, 1080, 1920, 2, '@搞笑BGM合集', 'recommend-video', 670000, 23000, 18000),
(1005, 'https://dy.ttentau.top/0.mp4', '', '这猫咪成精了 #萌宠 #猫咪', 10.5, 1080, 1920, 3, '@宠物星球', 'recommend-video', 430000, 15000, 9000),
(1006, 'https://dy.ttentau.top/0.mp4', '', '挑战全网最辣火锅 #美食 #挑战', 25.0, 1080, 1920, 3, '@美食天下', 'recommend-video', 780000, 42000, 31000),
(1007, 'https://dy.ttentau.top/1.mp4', '', '这条街最靓的仔就是我 #跳舞 #街舞', 16.0, 1080, 1920, 1, '@街舞少年', 'recommend-video', 340000, 11000, 7000),
(1008, 'https://dy.ttentau.top/1.mp4', '', '一道小学数学题难倒清华学霸 #教育 #数学', 20.0, 1080, 1920, 2, '@知识分享官', 'recommend-video', 950000, 67000, 52000);

-- 评论数据
INSERT INTO t_comment (id, video_id, user_id, content, like_count, reply_count, parent_id)
VALUES
(2001, 1001, 2, '笑死我了哈哈哈哈', 32000, 15, 0),
(2002, 1001, 3, '这个反转属实没想到', 18000, 8, 0),
(2003, 1001, 1, '感谢大家喜欢！下期更精彩', 45000, 22, 0),
(2004, 1002, 2, '这活整得可以', 12000, 3, 0),
(2005, 1002, 3, '看了三遍还是没学会', 8700, 5, 0),
(2006, 1003, 1, '太真实了，就是我本人', 28000, 12, 0),
(2007, 1003, 3, '第一天：精神小伙；一年后：活着的尸体', 35000, 18, 0);
