#pragma once

#include "common/frame.h"
#include <memory>
#include <deque>
#include <functional>

// Dynamic Bitrate Controller
// Monitors network conditions and adapts bitrate in real-time
// TikTok-like adaptive bitrate (ABR) algorithm

struct NetworkMetrics {
    double rtt_ms = 0.0;           // Round-trip time
    double packet_loss_ratio = 0.0; // Packet loss (0~1)
    double jitter_ms = 0.0;        // Jitter
    double throughput_bps = 0.0;    // Measured throughput
    int available_bw_bps = 0;      // Estimated available bandwidth
};

struct BitrateConfig {
    int initial_bitrate = 4000000;  // 4 Mbps
    int max_bitrate = 8000000;      // 8 Mbps
    int min_bitrate = 500000;       // 500 Kbps
    int current_bitrate = 4000000;

    // Adaptive parameters
    double up_scale_threshold = 0.75;    // Scale up when usage < 75%
    double down_scale_threshold = 0.90;  // Scale down when usage > 90%
    double scale_up_factor = 1.25;       // Multiply by 1.25 when scaling up
    double scale_down_factor = 0.75;     // Multiply by 0.75 when scaling down
    int min_duration_ms = 10000;         // Min time between bitrate changes
    int increase_cooldown_ms = 30000;    // Cooldown after increasing

    // Quality tiers (bitrate in bps)
    static constexpr int TIER_8K = 8000000;    // 8 Mbps  - 1080P60
    static constexpr int TIER_4K = 4000000;    // 4 Mbps  - 1080P30
    static constexpr int TIER_2K = 2000000;    // 2 Mbps  - 720P60
    static constexpr int TIER_1K = 1000000;    // 1 Mbps  - 720P30
    static constexpr int TIER_500K = 500000;   // 500 Kbps - 480P30
};

class BitrateController {
public:
    BitrateController();
    ~BitrateController();

    void init(const BitrateConfig& config);
    void set_config(const BitrateConfig& config);

    // Feed network metrics, returns recommended bitrate
    int update(const NetworkMetrics& metrics, int64_t now_ms);

    // Get current target bitrate
    int current_bitrate() const { return config_.current_bitrate; }

    // Force specific bitrate
    void force_bitrate(int bitrate);

    // Reset to initial
    void reset();

    using BitrateChangeCallback = std::function<void(int old_bitrate, int new_bitrate)>;
    void set_change_callback(BitrateChangeCallback cb) { change_cb_ = std::move(cb); }

private:
    BitrateConfig config_;
    BitrateChangeCallback change_cb_;
    int64_t last_change_ms_ = 0;
    int64_t last_increase_ms_ = 0;
    std::deque<double> loss_history_;
    std::deque<int64_t> rtt_history_;

    int compute_next_bitrate(const NetworkMetrics& metrics, int64_t now_ms);
    bool should_scale_up(const NetworkMetrics& metrics) const;
    bool should_scale_down(const NetworkMetrics& metrics) const;
    int clamp_bitrate(int bitrate) const;
};
