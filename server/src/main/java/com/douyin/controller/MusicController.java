package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.entity.Music;
import com.douyin.service.MusicService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicService musicService;

    public MusicController(MusicService musicService) {
        this.musicService = musicService;
    }

    /** 搜索本地曲库 */
    @GetMapping("/search")
    public Result<Map<String, Object>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<Map<String, Object>> list = musicService.search(keyword, pageNo, pageSize);
        return Result.ok(Map.of("list", list, "total", list.size()));
    }

    /** 歌曲详情 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        Map<String, Object> detail = musicService.getDetail(id);
        if (detail.isEmpty()) return Result.fail("歌曲不存在");
        return Result.ok(detail);
    }

    /** 全量列表 */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        List<Map<String, Object>> list = musicService.list(pageNo, pageSize);
        return Result.ok(Map.of("list", list, "total", list.size()));
    }

    /** 热门推荐 */
    @GetMapping("/hot")
    public Result<List<Map<String, Object>>> hot(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(musicService.hot(limit));
    }

    /** 上传音乐文件 */
    @PostMapping("/upload")
    public Result<Music> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String artist,
            @RequestParam(defaultValue = "") String album) {
        try {
            Music music = musicService.upload(file, name, artist, album);
            return Result.ok(music);
        } catch (Exception e) {
            log.error("音乐上传失败", e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }
}
