package com.douyin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.douyin.entity.SearchAlias;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SearchAliasMapper extends BaseMapper<SearchAlias> {

    /** 根据用户搜索词查找所有别名映射, 按权重降序 */
    @Select("SELECT * FROM t_search_alias WHERE alias = #{keyword} ORDER BY weight DESC")
    List<SearchAlias> findByKeyword(@Param("keyword") String keyword);

    /** 自动挖掘: 查找与指定分类共现频率最高的关键词, 用于冷启动推荐 */
    @Select("SELECT vc.keywords FROM t_video_content vc " +
            "WHERE vc.text_category = #{category} AND vc.extract_status = 1 " +
            "LIMIT 100")
    List<String> findCoOccurringKeywords(@Param("category") String category);
}
