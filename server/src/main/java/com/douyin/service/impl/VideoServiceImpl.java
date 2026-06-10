package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.common.PageDTO;
import com.douyin.entity.Follow;
import com.douyin.entity.Like;
import com.douyin.entity.User;
import com.douyin.entity.Video;
import com.douyin.entity.VideoCollect;
import com.douyin.entity.WatchHistory;
import com.douyin.mapper.FollowMapper;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoCollectMapper;
import com.douyin.mapper.VideoMapper;
import com.douyin.mapper.WatchHistoryMapper;
import com.douyin.service.ContentFeatureService;
import com.douyin.service.RecommendationEngine;
import com.douyin.service.VideoService;
import com.douyin.vo.UserVO;
import com.douyin.vo.VideoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    private final UserMapper userMapper;
    private final LikeMapper likeMapper;
    private final FollowMapper followMapper;
    private final VideoCollectMapper collectMapper;
    private final WatchHistoryMapper watchHistoryMapper;
    private final RecommendationEngine recommendationEngine;
    private final ContentFeatureService contentFeatureService;

    public VideoServiceImpl(UserMapper userMapper, LikeMapper likeMapper, FollowMapper followMapper,
                            VideoCollectMapper collectMapper, WatchHistoryMapper watchHistoryMapper,
                            RecommendationEngine recommendationEngine,
                            ContentFeatureService contentFeatureService) {
        this.userMapper = userMapper;
        this.likeMapper = likeMapper;
        this.followMapper = followMapper;
        this.collectMapper = collectMapper;
        this.watchHistoryMapper = watchHistoryMapper;
        this.recommendationEngine = recommendationEngine;
        this.contentFeatureService = contentFeatureService;
    }

    @Override
    public PageDTO<VideoVO> getRecommended(Long viewerUserId, int start, int pageSize, String type) {
        Double minDuration = "long-video".equals(type) ? 60.0 : null;

        if (viewerUserId != null && start == 0) {
            // 首页个性化推荐
            List<Long> rankedIds = recommendationEngine.recommend(viewerUserId,
                    pageSize * 2, minDuration);
            if (!rankedIds.isEmpty()) {
                List<Video> videos = listByIds(rankedIds.subList(0, Math.min(pageSize, rankedIds.size())));
                videos = videos.stream().filter(v -> "APPROVED".equals(v.getStatus())).toList();
                // 恢复排序
                Map<Long, Video> videoMap = videos.stream()
                        .collect(Collectors.toMap(Video::getId, v -> v));
                List<Video> ordered = rankedIds.stream()
                        .map(videoMap::get).filter(Objects::nonNull).limit(pageSize).toList();
                List<VideoVO> voList = toVideoVOList(ordered, viewerUserId);
                return new PageDTO<>((long) rankedIds.size(), 1, pageSize, voList);
            }
        }

        // 非首页 / 未登录 / 引擎无结果 → 简单时间排序兜底
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        if (minDuration != null) wrapper.ge(Video::getDuration, minDuration);
        int pageNo = start / pageSize + 1;
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getFollowingVideos(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId == null) return new PageDTO<>(0, pageNo, pageSize, List.of());
        // 查询关注用户ID列表
        List<Long> followedIds = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                        .eq(Follow::getUserId, viewerUserId))
                .stream().map(Follow::getFollowId).toList();
        if (followedIds.isEmpty()) return new PageDTO<>(0, pageNo, pageSize, List.of());

        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getAuthorUserId, followedIds)
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getTrendingVideos(Long viewerUserId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("recommend-video", "image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getLikeCount)
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getUserVideos(Long viewerUserId, Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getAuthorUserId, userId)
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getMyVideos(Long viewerUserId, Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getAuthorUserId, userId)
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getLikedVideos(Long userId, int pageNo, int pageSize) {
        // 从 t_like 查出该用户点赞的所有视频ID
        LambdaQueryWrapper<Like> likeWrapper = new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .orderByDesc(Like::getCreateTime);
        IPage<Like> likePage = likeMapper.selectPage(new Page<>(pageNo, pageSize), likeWrapper);
        List<Long> videoIds = likePage.getRecords().stream()
                .map(Like::getVideoId).toList();

        if (videoIds.isEmpty()) return new PageDTO<>(likePage.getTotal(), pageNo, pageSize, List.of());

        List<Video> videos = listByIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus())).toList();
        // 按点赞顺序排列
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a, LinkedHashMap::new));
        List<Video> ordered = videoIds.stream()
                .map(videoMap::get).filter(Objects::nonNull).toList();

        List<VideoVO> voList = toVideoVOList(ordered, userId);
        return new PageDTO<>(likePage.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getPrivateVideos(Long viewerUserId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getType, "private");
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getHistory(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId == null) return new PageDTO<>(0, pageNo, pageSize, List.of());
        int offset = (pageNo - 1) * pageSize;
        List<Long> videoIds = watchHistoryMapper.findHistoryVideoIds(viewerUserId, offset, pageSize);
        if (videoIds.isEmpty()) return new PageDTO<>(0, pageNo, pageSize, List.of());
        List<Video> videos = baseMapper.selectBatchIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus())).toList();
        // 恢复原始顺序
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v));
        List<Video> ordered = videoIds.stream()
                .map(videoMap::get)
                .filter(Objects::nonNull)
                .toList();
        List<VideoVO> voList = toVideoVOList(ordered, viewerUserId);
        return new PageDTO<>((long) videoIds.size(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getHistoryOther(int pageNo, int pageSize) {
        return new PageDTO<>(0, pageNo, pageSize, List.of());
    }

    @Override
    public PageDTO<VideoVO> getRecommendedPosts(Long viewerUserId, int pageNo, int pageSize) {
        if (viewerUserId != null && pageNo == 1) {
            // 首页：走推荐引擎，然后仅保留 image/text 类型
            List<Long> rankedIds = recommendationEngine.recommend(viewerUserId,
                    pageSize * 4, null);
            if (!rankedIds.isEmpty()) {
                List<Video> videos = listByIds(rankedIds);
                videos = videos.stream().filter(v -> "APPROVED".equals(v.getStatus())).toList();
                Map<Long, Video> videoMap = videos.stream()
                        .collect(Collectors.toMap(Video::getId, v -> v));
                List<Video> ordered = rankedIds.stream()
                        .map(videoMap::get)
                        .filter(Objects::nonNull)
                        .filter(v -> "image".equals(v.getType()) || "text".equals(v.getType()))
                        .limit(pageSize).toList();
                if (!ordered.isEmpty()) {
                    List<VideoVO> voList = toVideoVOList(ordered, viewerUserId);
                    return new PageDTO<>((long) rankedIds.size(), 1, pageSize, voList);
                }
            }
        }

        // 兜底：简单时间排序
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .in(Video::getType, List.of("image", "text"))
                .eq(Video::getStatus, "APPROVED")
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getRecommendedGoods(int pageNo, int pageSize) {
        return new PageDTO<>(0, pageNo, pageSize, List.of());
    }

    /** 将 Video 列表转换为 VideoVO 列表, 嵌入 author 信息, 批量查询当前用户的点赞状态 */
    private List<VideoVO> toVideoVOList(List<Video> videos, Long viewerUserId) {
        if (videos.isEmpty()) return List.of();

        // 批量查询作者信息
        List<Long> authorIds = videos.stream()
                .map(Video::getAuthorUserId).distinct().toList();
        List<User> users = userMapper.selectBatchIds(authorIds);
        Map<Long, UserVO> userMap = users.stream()
                .collect(Collectors.toMap(User::getUid, UserVO::from));

        // 批量查询当前用户的点赞状态
        Set<Long> likedVideoIds = Set.of();
        if (viewerUserId != null) {
            List<Long> videoIds = videos.stream().map(Video::getId).toList();
            likedVideoIds = likeMapper.selectList(new LambdaQueryWrapper<Like>()
                            .eq(Like::getUserId, viewerUserId)
                            .in(Like::getVideoId, videoIds))
                    .stream().map(Like::getVideoId)
                    .collect(Collectors.toSet());
        }

        // 批量查询当前用户是否关注了这些作者
        Set<Long> finalLiked = likedVideoIds;
        Set<Long> followedAuthorIds = Set.of();
        if (viewerUserId != null && !authorIds.isEmpty()) {
            followedAuthorIds = followMapper.selectList(new LambdaQueryWrapper<Follow>()
                            .eq(Follow::getUserId, viewerUserId)
                            .in(Follow::getFollowId, authorIds))
                    .stream().map(Follow::getFollowId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalFollowed = followedAuthorIds;

        // 批量查询当前用户的收藏状态
        Set<Long> collectedVideoIds = Set.of();
        if (viewerUserId != null) {
            List<Long> videoIds = videos.stream().map(Video::getId).toList();
            collectedVideoIds = collectMapper.selectList(new LambdaQueryWrapper<VideoCollect>()
                            .eq(VideoCollect::getUserId, viewerUserId)
                            .in(VideoCollect::getVideoId, videoIds))
                    .stream().map(VideoCollect::getVideoId)
                    .collect(Collectors.toSet());
        }
        Set<Long> finalCollected = collectedVideoIds;

        return videos.stream()
                .map(v -> VideoVO.from(v,
                        userMap.getOrDefault(v.getAuthorUserId(), null),
                        finalLiked.contains(v.getId()),
                        finalFollowed.contains(v.getAuthorUserId()),
                        finalCollected.contains(v.getId())))
                .toList();
    }

    @Override
    public boolean toggleLike(Long userId, Long videoId) {
        try {
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, userId)
                    .eq(Like::getVideoId, videoId);
            Like exist = likeMapper.selectOne(wrapper);
            Video video = getById(videoId);
            if (video == null) return false;
            if (exist != null) {
                likeMapper.deleteById(exist.getId());
                video.setLikeCount(Math.max(0, (video.getLikeCount() != null ? video.getLikeCount() : 0) - 1));
                updateById(video);
                updateAuthorFavorited(video.getAuthorUserId(), -1);
                return false;
            }
            Like like = new Like();
            like.setUserId(userId);
            like.setVideoId(videoId);
            likeMapper.insert(like);
            video.setLikeCount((video.getLikeCount() != null ? video.getLikeCount() : 0) + 1);
            updateById(video);
            updateAuthorFavorited(video.getAuthorUserId(), 1);
            return true;
        } catch (Exception e) {
            log.error("toggleLike: exception occurred", e);
            return false;
        }
    }

    private void updateAuthorFavorited(Long authorId, int delta) {
        if (authorId == null) return;
        try {
            User author = userMapper.selectById(authorId);
            if (author != null) {
                author.setTotalFavorited(Math.max(0,
                        (author.getTotalFavorited() != null ? author.getTotalFavorited() : 0) + delta));
                userMapper.updateById(author);
            }
        } catch (Exception e) {
            log.error("updateAuthorFavorited failed: authorId={}, delta={}", authorId, delta, e);
        }
    }

    @Override
    public boolean hasLiked(Long userId, Long videoId) {
        return likeMapper.selectCount(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getVideoId, videoId)) > 0;
    }

    @Override
    @Transactional
    public long recordShare(Long videoId) {
        Video video = getById(videoId);
        if (video == null) return 0;
        video.setShareCount((video.getShareCount() != null ? video.getShareCount() : 0) + 1);
        updateById(video);
        return video.getShareCount();
    }

    @Override
    @Transactional
    public boolean toggleCollect(Long userId, Long videoId) {
        LambdaQueryWrapper<VideoCollect> wrapper = new LambdaQueryWrapper<VideoCollect>()
                .eq(VideoCollect::getUserId, userId)
                .eq(VideoCollect::getVideoId, videoId);
        VideoCollect exist = collectMapper.selectOne(wrapper);
        Video video = getById(videoId);
        if (video == null) return false;
        if (exist != null) {
            collectMapper.deleteById(exist.getId());
            video.setCollectCount(Math.max(0, (video.getCollectCount() != null ? video.getCollectCount() : 0) - 1));
            updateById(video);
            return false;
        }
        VideoCollect collect = new VideoCollect();
        collect.setUserId(userId);
        collect.setVideoId(videoId);
        collectMapper.insert(collect);
        video.setCollectCount((video.getCollectCount() != null ? video.getCollectCount() : 0) + 1);
        updateById(video);
        return true;
    }

    @Override
    public PageDTO<VideoVO> getCollectedVideos(Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<VideoCollect> collectWrapper = new LambdaQueryWrapper<VideoCollect>()
                .eq(VideoCollect::getUserId, userId)
                .orderByDesc(VideoCollect::getCreateTime);
        IPage<VideoCollect> collectPage = collectMapper.selectPage(new Page<>(pageNo, pageSize), collectWrapper);
        List<Long> videoIds = collectPage.getRecords().stream()
                .map(VideoCollect::getVideoId).toList();

        if (videoIds.isEmpty()) return new PageDTO<>(collectPage.getTotal(), pageNo, pageSize, List.of());

        List<Video> videos = listByIds(videoIds).stream()
                .filter(v -> "APPROVED".equals(v.getStatus())).toList();
        Map<Long, Video> videoMap = videos.stream()
                .collect(Collectors.toMap(Video::getId, v -> v, (a, b) -> a, LinkedHashMap::new));
        List<Video> ordered = videoIds.stream()
                .map(videoMap::get).filter(Objects::nonNull).toList();

        List<VideoVO> voList = toVideoVOList(ordered, userId);
        return new PageDTO<>(collectPage.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public List<VideoVO> searchVideos(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();
        List<Video> videos = baseMapper.searchByKeyword(keyword.trim());
        return toVideoVOList(videos, null);
    }

    @Override
    @Transactional
    public void recordWatch(Long userId, Long videoId, Long authorUserId,
                            double watchDuration, double videoDuration, boolean finished,
                            String trafficSource, String sessionId, double swipeSeconds) {
        if (userId == null || videoId == null) return;
        WatchHistory exist = watchHistoryMapper.selectOne(new LambdaQueryWrapper<WatchHistory>()
                .eq(WatchHistory::getUserId, userId)
                .eq(WatchHistory::getVideoId, videoId));
        if (exist != null) {
            if (watchDuration > (exist.getWatchDuration() != null ? exist.getWatchDuration() : 0)) {
                exist.setWatchDuration(watchDuration);
            }
            exist.setVideoDuration(videoDuration);
            if (finished) exist.setFinished(1);
            exist.setRepeatCount((exist.getRepeatCount() != null ? exist.getRepeatCount() : 0) + 1);
            if (trafficSource != null) exist.setTrafficSource(trafficSource);
            if (sessionId != null) exist.setSessionId(sessionId);
            if (swipeSeconds > 0) exist.setSwipeSeconds(swipeSeconds);
            watchHistoryMapper.updateById(exist);
        } else {
            WatchHistory wh = new WatchHistory();
            wh.setUserId(userId);
            wh.setVideoId(videoId);
            wh.setAuthorUserId(authorUserId);
            wh.setWatchDuration(watchDuration);
            wh.setVideoDuration(videoDuration);
            wh.setFinished(finished ? 1 : 0);
            wh.setRepeatCount(1);
            wh.setTrafficSource(trafficSource);
            wh.setSessionId(sessionId);
            wh.setSwipeSeconds(swipeSeconds);
            watchHistoryMapper.insert(wh);
        }
    }
}
