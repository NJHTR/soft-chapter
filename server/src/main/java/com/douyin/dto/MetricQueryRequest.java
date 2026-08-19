package com.douyin.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MetricQueryRequest {
    private List<String> metrics;
    private TimeRange timeRange;
    private String interval;       // 1h, 1d
    private List<Filter> filters;  // optional
    private List<String> groupBy;  // optional: content_type, traffic_source, device_os, user_segment
    private String compareWith;    // previous_period

    @Data
    public static class TimeRange {
        private String start;  // ISO datetime
        private String end;
    }

    @Data
    public static class Filter {
        private String field;
        private String operator;  // eq, ne, in
        private String value;
    }
}
