package com.douyin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.douyin.entity.Video;
import com.douyin.vo.VideoVO;
import com.douyin.common.PageDTO;

import java.util.List;

public interface VideoService extends IService<Video> {

    PageDTO<VideoVO> getRecommended(Long viewerUserId, int start, int pageSize);

    PageDTO<VideoVO> getUserVideos(Long viewerUserId, Long userId, int pageNo, int pageSize);

    PageDTO<VideoVO> getMyVideos(Long viewerUserId, Long userId, int pageNo, int pageSize);

    PageDTO<VideoVO> getLikedVideos(Long userId, int pageNo, int pageSize);

    PageDTO<VideoVO> getPrivateVideos(Long viewerUserId, int pageNo, int pageSize);

    PageDTO<VideoVO> getHistory(Long viewerUserId, int pageNo, int pageSize);

    /** 其他浏览历史(图文/商品等) — 暂返回空 */
    PageDTO<VideoVO> getHistoryOther(int pageNo, int pageSize);

    /** 获取图文推荐 */
    PageDTO<VideoVO> getRecommendedPosts(int pageNo, int pageSize);

    /** 获取商品推荐 */
    PageDTO<VideoVO> getRecommendedGoods(int pageNo, int pageSize);

    /** 切换点赞状态, 返回: true=已赞 false=已取消 */
    boolean toggleLike(Long userId, Long videoId);

    /** 当前用户是否已赞该视频 */
    boolean hasLiked(Long userId, Long videoId);

    /** 记录分享, 返回分享后计数 */
    long recordShare(Long videoId);

    /** 切换收藏状态, 返回: true=已收藏 false=已取消 */
    boolean toggleCollect(Long userId, Long videoId);

    /** 获取用户收藏的视频列表 */
    PageDTO<VideoVO> getCollectedVideos(Long userId, int pageNo, int pageSize);
}
