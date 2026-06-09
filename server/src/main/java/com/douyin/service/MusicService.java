package com.douyin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.douyin.entity.Music;
import com.douyin.mapper.MusicMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * 本地音乐曲库服务 (不依赖任何外部API)
 */
@Slf4j
@Service
public class MusicService {

    private final MusicMapper musicMapper;

    @Value("${music.local.dir:${user.dir}/music}")
    private String musicDir;

    @Value("${music.local.base-url:/music}")
    private String musicBaseUrl;

    public MusicService(MusicMapper musicMapper) {
        this.musicMapper = musicMapper;
    }

    /** 搜索本地曲库 */
    public List<Map<String, Object>> search(String keyword, int pageNo, int pageSize) {
        Page<Music> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<Music> wrapper = new LambdaQueryWrapper<Music>()
                .like(Music::getName, keyword).or()
                .like(Music::getArtist, keyword)
                .orderByDesc(Music::getCreateTime);
        Page<Music> result = musicMapper.selectPage(page, wrapper);
        return result.getRecords().stream().map(this::toMap).toList();
    }

    /** 歌曲详情 */
    public Map<String, Object> getDetail(Long id) {
        Music music = musicMapper.selectById(id);
        if (music == null) return Map.of();
        Map<String, Object> map = toMap(music);
        map.put("play_url", music.getPlayUrl() != null ? music.getPlayUrl() : "");
        map.put("lyric", music.getLyric() != null ? music.getLyric() : "");
        return map;
    }

    /** 全量列表 (分页) */
    public List<Map<String, Object>> list(int pageNo, int pageSize) {
        LambdaQueryWrapper<Music> wrapper = new LambdaQueryWrapper<Music>()
                .orderByDesc(Music::getCreateTime);
        Page<Music> page = new Page<>(pageNo, pageSize);
        return musicMapper.selectPage(page, wrapper).getRecords().stream().map(this::toMap).toList();
    }

    /** 热门推荐: 按被使用次数/创建时间排序 */
    public List<Map<String, Object>> hot(int limit) {
        LambdaQueryWrapper<Music> wrapper = new LambdaQueryWrapper<Music>()
                .orderByDesc(Music::getCreateTime)
                .last("LIMIT " + limit);
        return musicMapper.selectList(wrapper).stream().map(this::toMap).toList();
    }

    /** 上传音乐文件 */
    public Music upload(MultipartFile file, String name, String artist, String album) throws IOException {
        Path dir = Path.of(musicDir);
        Files.createDirectories(dir);

        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "music.mp3";
        String ext = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf(".")) : ".mp3";
        String fileName = UUID.randomUUID() + ext;
        Path dest = dir.resolve(fileName);
        file.transferTo(dest.toFile());

        Music music = new Music();
        music.setName(name != null && !name.isEmpty() ? name : originalName);
        music.setArtist(artist != null ? artist : "未知歌手");
        music.setAlbum(album != null ? album : "");
        music.setDuration(getDuration(dest));
        music.setSource("local");
        music.setPlayUrl(musicBaseUrl + "/" + fileName);
        music.setStatus("PENDING");
        musicMapper.insert(music);
        log.info("音乐上传完成: id={} name={} path={}", music.getId(), music.getName(), dest);
        return music;
    }

    /** 根据ID获取 Music 实体 */
    public Music getById(Long id) {
        return musicMapper.selectById(id);
    }

    private Map<String, Object> toMap(Music m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("name", m.getName());
        map.put("artist", m.getArtist());
        map.put("album", m.getAlbum());
        map.put("cover_url", m.getCoverUrl());
        map.put("play_url", m.getPlayUrl());
        map.put("duration", m.getDuration());
        map.put("source", m.getSource());
        return map;
    }

    private int getDuration(Path file) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", file.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor() == 0) return (int) Double.parseDouble(out);
        } catch (Exception ignored) {}
        return 0;
    }
}
