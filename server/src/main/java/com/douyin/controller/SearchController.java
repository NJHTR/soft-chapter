package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.service.SearchSuggestionService;
import com.douyin.service.SearchSuggestionService.SuggestionItem;
import com.douyin.service.SearchSuggestionService.SuggestionResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchSuggestionService suggestionService;

    public SearchController(SearchSuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    /**
     * 搜索输入联想: 用户输入前缀 → 返回建议词条 + 热门搜索
     */
    @GetMapping("/search/suggestions")
    public Result<Map<String, Object>> suggestions(
            @RequestParam String q,
            @RequestParam(required = false) Long userId,
            @RequestParam(value = "video_id", required = false) Long videoId,
            @RequestParam(defaultValue = "8") int limit) {

        SuggestionResult result = suggestionService.suggest(q, userId, videoId, limit);

        return Result.ok(Map.of(
                "suggestions", result.suggestions(),
                "hotQueries", result.hotQueries()
        ));
    }

    /** "猜你想搜" 默认推荐词条 */
    @GetMapping("/search/guess")
    public Result<List<Map<String, Object>>> guess(@RequestParam(required = false) Long userId) {
        List<Map<String, Object>> list = suggestionService.guessYouWant(userId, 12);
        return Result.ok(list);
    }

    /** 搜索热榜: 按全局搜索频率排名 */
    @GetMapping("/search/hot-rank")
    public Result<List<Map<String, Object>>> hotRank(@RequestParam(defaultValue = "30") int limit) {
        List<Map<String, Object>> list = suggestionService.hotRank(limit);
        return Result.ok(list);
    }

    /**
     * 视频页"猜你想搜": 基于当前视频推荐搜索词条
     */
    @GetMapping("/video/{videoId}/search-hints")
    public Result<Map<String, Object>> searchHints(
            @PathVariable Long videoId,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "5") int limit) {

        List<SuggestionItem> hints = suggestionService.searchHints(videoId, userId, limit);

        return Result.ok(Map.of("hints", hints));
    }

    /** AI 智能搜索总结 */
    @GetMapping("/search/summary")
    public Result<Map<String, Object>> summary(@RequestParam String keyword) {
        String kw = keyword.trim();
        if (kw.isEmpty()) return Result.ok(Map.of("summary", "请输入搜索关键词"));

        String summary = suggestionService.generateSummary(kw);
        return Result.ok(Map.of("summary", summary));
    }
}
