package com.douyin.controller;

import com.douyin.common.Result;
import com.douyin.mapper.SearchHistoryMapper;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search-history")
public class SearchHistoryController {

    private final SearchHistoryMapper searchHistoryMapper;
    private final JwtUtil jwtUtil;

    public SearchHistoryController(SearchHistoryMapper searchHistoryMapper, JwtUtil jwtUtil) {
        this.searchHistoryMapper = searchHistoryMapper;
        this.jwtUtil = jwtUtil;
    }

    private Long getLoginUserId(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try { return jwtUtil.getUserIdFromToken(auth.substring(7)); } catch (Exception ignored) {}
        }
        return null;
    }

    /** 获取当前用户的搜索历史 */
    @GetMapping
    public Result<List<String>> list(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.ok(List.of());
        return Result.ok(searchHistoryMapper.findKeywordsByUserId(userId));
    }

    /** 添加搜索记录 */
    @PostMapping
    public Result<?> save(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        String keyword = body.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) return Result.fail("关键词不能为空");
        searchHistoryMapper.insertKeyword(userId, keyword.trim());
        return Result.ok();
    }

    /** 清空搜索历史 */
    @DeleteMapping
    public Result<?> clear(HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        searchHistoryMapper.deleteByUserId(userId);
        return Result.ok();
    }

    /** 删除单条搜索记录(按关键词) */
    @DeleteMapping("/keyword")
    public Result<?> deleteByKeyword(@RequestBody Map<String, String> body, HttpServletRequest req) {
        Long userId = getLoginUserId(req);
        if (userId == null) return Result.fail("请先登录");
        String keyword = body.get("keyword");
        if (keyword == null || keyword.trim().isEmpty()) return Result.fail("关键词不能为空");
        searchHistoryMapper.deleteByKeyword(userId, keyword.trim());
        return Result.ok();
    }
}
