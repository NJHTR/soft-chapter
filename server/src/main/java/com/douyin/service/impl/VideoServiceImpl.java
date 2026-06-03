package com.douyin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.douyin.common.PageDTO;
import com.douyin.entity.Like;
import com.douyin.entity.User;
import com.douyin.entity.Video;
import com.douyin.mapper.LikeMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.mapper.VideoMapper;
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

    public VideoServiceImpl(UserMapper userMapper, LikeMapper likeMapper) {
        this.userMapper = userMapper;
        this.likeMapper = likeMapper;
    }

    @Override
    public PageDTO<VideoVO> getRecommended(Long viewerUserId, int start, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getType, "recommend-video")
                .orderByDesc(Video::getCreateTime);
        int pageNo = start / pageSize + 1;
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getUserVideos(Long viewerUserId, Long userId, int pageNo, int pageSize) {
        LambdaQueryWrapper<Video> wrapper = new LambdaQueryWrapper<Video>()
                .eq(Video::getAuthorUserId, userId)
                .orderByDesc(Video::getCreateTime);
        IPage<Video> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<VideoVO> voList = toVideoVOList(page.getRecords(), viewerUserId);
        return new PageDTO<>(page.getTotal(), pageNo, pageSize, voList);
    }

    @Override
    public PageDTO<VideoVO> getMyVideos(Long viewerUserId, Long userId, int pageNo, int pageSize) {
        return getUserVideos(viewerUserId, userId, pageNo, pageSize);
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

        List<Video> videos = listByIds(videoIds);
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
        return getLikedVideos(viewerUserId, pageNo, pageSize);
    }

    @Override
    public PageDTO<VideoVO> getHistoryOther(int pageNo, int pageSize) {
        return new PageDTO<>(0, pageNo, pageSize, List.of());
    }

    @Override
    public PageDTO<VideoVO> getRecommendedPosts(int pageNo, int pageSize) {
        return new PageDTO<>(0, pageNo, pageSize, List.of());
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

        Set<Long> finalLiked = likedVideoIds;
        return videos.stream()
                .map(v -> VideoVO.from(v,
                        userMap.getOrDefault(v.getAuthorUserId(), null),
                        finalLiked.contains(v.getId()),
                        false))
                .toList();
    }

    @Override
    public boolean toggleLike(Long userId, Long videoId) {
        log.info("toggleLike called: userId={}, videoId={}", userId, videoId);
        try {
            LambdaQueryWrapper<Like> wrapper = new LambdaQueryWrapper<Like>()
                    .eq(Like::getUserId, userId)
                    .eq(Like::getVideoId, videoId);
            Like exist = likeMapper.selectOne(wrapper);
            log.info("toggleLike: exist={}", exist != null);
            Video video = getById(videoId);
            if (video == null) {
                log.warn("toggleLike: video not found for id={}", videoId);
                return false;
            }
            log.info("toggleLike: video found, current likeCount={}", video.getLikeCount());
            if (exist != null) {
                log.info("toggleLike: unlike, deleting like record id={}", exist.getId());
                likeMapper.deleteById(exist.getId());
                video.setLikeCount(Math.max(0, (video.getLikeCount() != null ? video.getLikeCount() : 0) - 1));
                updateById(video);
                return false;
            }
            Like like = new Like();
            like.setUserId(userId);
            like.setVideoId(videoId);
            log.info("toggleLike: about to insert like: userId={}, videoId={}", like.getUserId(), like.getVideoId());
            int rows = likeMapper.insert(like);
            log.info("toggleLike: insert rows={}, generated id={}", rows, like.getId());
            video.setLikeCount((video.getLikeCount() != null ? video.getLikeCount() : 0) + 1);
            boolean updated = updateById(video);
            log.info("toggleLike: updateById result={}, new likeCount={}", updated, video.getLikeCount());
            return true;
        } catch (Exception e) {
            log.error("toggleLike: exception occurred", e);
            return false;
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
}
