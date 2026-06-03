package com.douyin.controller;

import com.douyin.common.PageDTO;
import com.douyin.common.Result;
import com.douyin.service.VideoService;
import com.douyin.vo.VideoVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final VideoService videoService;

    public ShopController(VideoService videoService) {
        this.videoService = videoService;
    }

    /** 商品推荐 */
    @GetMapping("/recommended")
    public Result<PageDTO<VideoVO>> recommended(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(videoService.getRecommendedGoods(pageNo, pageSize));
    }
}
