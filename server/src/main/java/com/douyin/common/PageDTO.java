package com.douyin.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> {
    private long total;
    private int pageNo;
    private int pageSize;
    private List<T> list;

    public PageDTO(long total, int pageNo, int pageSize, List<T> voList) {
    }

    public static <T> PageDTO<T> of(IPage<T> page) {
        return new PageDTO<>(
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize(),
                page.getRecords()
        );
    }
}
