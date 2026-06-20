package com.douyin.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.douyin.common.Result;
import com.douyin.entity.SearchAlias;
import com.douyin.entity.User;
import com.douyin.mapper.SearchAliasMapper;
import com.douyin.mapper.UserMapper;
import com.douyin.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
public class SearchConfigController {

    private final SearchAliasMapper aliasMapper;
    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public SearchConfigController(SearchAliasMapper aliasMapper, JwtUtil jwtUtil, UserMapper userMapper) {
        this.aliasMapper = aliasMapper;
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    private User checkAdmin(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            try {
                Long userId = jwtUtil.getUserIdFromToken(auth.substring(7));
                if (userId != null) {
                    User user = userMapper.selectById(userId);
                    if (user != null && "ADMIN".equals(user.getRole())) return user;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ========== Search Alias CRUD ==========

    @GetMapping("/search-alias/list")
    public Result<List<SearchAlias>> list(@RequestParam(required = false) String keyword,
                                           HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");

        LambdaQueryWrapper<SearchAlias> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(SearchAlias::getAlias, keyword);
        }
        wrapper.orderByDesc(SearchAlias::getWeight);
        return Result.ok(aliasMapper.selectList(wrapper));
    }

    @PostMapping("/search-alias")
    public Result<?> create(@RequestBody SearchAlias alias, HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");
        if (alias.getAlias() == null || alias.getAlias().trim().isEmpty()) {
            return Result.fail("alias is required");
        }
        alias.setId(null);
        aliasMapper.insert(alias);
        log.info("Admin created search alias: {} → category={} keyword={}",
                alias.getAlias(), alias.getTargetCategory(), alias.getTargetKeyword());
        return Result.ok(alias);
    }

    @PutMapping("/search-alias/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody SearchAlias alias,
                            HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");
        SearchAlias exist = aliasMapper.selectById(id);
        if (exist == null) return Result.fail("Alias not found");

        exist.setAlias(alias.getAlias());
        exist.setTargetCategory(alias.getTargetCategory());
        exist.setTargetKeyword(alias.getTargetKeyword());
        exist.setWeight(alias.getWeight());
        exist.setSource(alias.getSource());
        aliasMapper.updateById(exist);
        return Result.ok(exist);
    }

    @DeleteMapping("/search-alias/{id}")
    public Result<?> delete(@PathVariable Long id, HttpServletRequest req) {
        if (checkAdmin(req) == null) return Result.fail("No admin permission");
        aliasMapper.deleteById(id);
        return Result.ok(Map.of("deleted", id));
    }
}
