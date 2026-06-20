package com.douyin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 搜索别名映射: 将用户的模糊搜索词映射到平台内容体系中的分类/关键词
 *
 * 例如: alias="好吃的" → targetCategory="美食", targetKeyword="美食教程"
 * 由运营后台或数据分析自动填充, 不硬编码在 Java 代码中
 */
@Data
@TableName("t_search_alias")
public class SearchAlias {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户输入的搜索词 */
    private String alias;

    /** 映射到的目标分类 (匹配 t_video_content.text_category) */
    private String targetCategory;

    /** 映射到的目标关键词 (匹配 t_video_content.keywords JSON数组) */
    private String targetKeyword;

    /** 权重 0-1, 越高优先展示 */
    private Double weight;

    /** 0=运营配置 1=自动挖掘 */
    private Integer source;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
