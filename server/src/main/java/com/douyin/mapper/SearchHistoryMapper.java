package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.SearchHistory;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SearchHistoryMapper extends BaseMapper<SearchHistory> {

    /** 获取用户去重后的搜索关键词，按最近搜索时间排序 */
    @Select("SELECT keyword FROM t_search_history WHERE user_id = #{userId} GROUP BY keyword ORDER BY MAX(create_time) DESC LIMIT 50")
    List<String> findKeywordsByUserId(Long userId);

    /** 插入搜索记录 */
    @Insert("INSERT INTO t_search_history (user_id, keyword) VALUES (#{userId}, #{keyword})")
    int insertKeyword(Long userId, String keyword);

    /** 清空用户所有搜索历史 */
    @Delete("DELETE FROM t_search_history WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);

    /** 删除用户某关键词的所有记录 */
    @Delete("DELETE FROM t_search_history WHERE user_id = #{userId} AND keyword = #{keyword}")
    int deleteByKeyword(Long userId, String keyword);
}
