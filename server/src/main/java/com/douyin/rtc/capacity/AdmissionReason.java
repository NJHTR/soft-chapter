package com.douyin.rtc.capacity;

/**
 * 决策原因（低基数枚举；维度级原因携带维度名，见 {@link #overCapacity}）。
 */
public record AdmissionReason(String code, String dimension) {

    public static final AdmissionReason OK =
            new AdmissionReason("ok", null);
    public static final AdmissionReason DISABLED =
            new AdmissionReason("feature_disabled", null);
    public static final AdmissionReason UNKNOWN_NODE =
            new AdmissionReason("unknown_node", null);
    public static final AdmissionReason STALE_OR_MISSING_SNAPSHOT =
            new AdmissionReason("stale_or_missing_snapshot", null);
    public static final AdmissionReason WARN_THRESHOLD =
            new AdmissionReason("warn_threshold", null);

    public static AdmissionReason overCapacity(CapacityDimension dim) {
        return new AdmissionReason("over_capacity", dim.label());
    }

    @Override
    public String toString() {
        return dimension == null ? code : code + ":" + dimension;
    }
}