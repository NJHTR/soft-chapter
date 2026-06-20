package com.douyin.service;

import com.douyin.entity.SearchAlias;
import com.douyin.entity.Video;
import com.douyin.mapper.SearchAliasMapper;
import com.douyin.mapper.VideoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 搜索编排服务: 别名扩展 → 多字段加权检索 → 结果排序
 *
 * 不硬编码任何关键词映射, 所有扩展规则来自 t_search_alias 表
 */
@Slf4j
@Service
public class SearchService {

    private final VideoMapper videoMapper;
    private final SearchAliasMapper aliasMapper;

    public SearchService(VideoMapper videoMapper, SearchAliasMapper aliasMapper) {
        this.videoMapper = videoMapper;
        this.aliasMapper = aliasMapper;
    }

    /**
     * 多字段加权搜索
     *
     * @param rawKeyword 用户输入的原始搜索词
     * @param limit      返回数量上限
     * @return 按相关性降序的视频列表
     */
    public List<Video> search(String rawKeyword, int limit) {
        if (rawKeyword == null || rawKeyword.trim().isEmpty()) return List.of();

        String keyword = rawKeyword.trim();

        // 1. 查询别名映射表, 获取扩展搜索词
        List<SearchAlias> aliases = aliasMapper.findByKeyword(keyword);

        // 2. 构建分类列表和关键词列表
        List<String> categories = aliases.stream()
                .map(SearchAlias::getTargetCategory)
                .filter(Objects::nonNull)
                .filter(c -> !c.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        List<String> keywords = new ArrayList<>();
        keywords.add(keyword); // 原始搜索词始终参与
        aliases.stream()
                .map(SearchAlias::getTargetKeyword)
                .filter(Objects::nonNull)
                .filter(k -> !k.isEmpty() && !k.equals(keyword))
                .distinct()
                .forEach(keywords::add);

        log.debug("搜索扩展: raw={} categories={} keywords={}", keyword, categories, keywords);

        // 3. 执行多字段加权搜索
        return videoMapper.searchByKeywords(categories, keywords, limit);
    }

    /**
     * 兼容旧接口: 单关键词简单搜索 (不扩展别名, 不 JOIN)
     * 仅在别名表无数据或多字段搜索异常时回退使用
     */
    public List<Video> simpleSearch(String keyword, int limit) {
        return videoMapper.searchByKeyword(keyword);
    }
}
