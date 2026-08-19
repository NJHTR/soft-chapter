#include "encoder/bitrate_controller.h"
#include <algorithm>
#include <iostream>

BitrateController::BitrateController() = default;
BitrateController::~BitrateController() = default;

void BitrateController::init(const BitrateConfig& config) {
    config_ = config;
    config_.current_bitrate = config_.initial_bitrate;
}

void BitrateController::set_config(const BitrateConfig& config) {
    config_ = config;
}

int BitrateController::update(const NetworkMetrics& metrics, int64_t now_ms) {
    // Track history
    loss_history_.push_back(metrics.packet_loss_ratio);
    if (loss_history_.size() > 10) loss_history_.pop_front();

    rtt_history_.push_back(static_cast<int64_t>(metrics.rtt_ms));
    if (rtt_history_.size() > 10) rtt_history_.pop_front();

    int new_bitrate = compute_next_bitrate(metrics, now_ms);
    int clamped = clamp_bitrate(new_bitrate);

    if (clamped != config_.current_bitrate) {
        int old = config_.current_bitrate;
        config_.current_bitrate = clamped;
        last_change_ms_ = now_ms;
        if (clamped > old) last_increase_ms_ = now_ms;

        if (change_cb_) {
            change_cb_(old, clamped);
        }
    }

    return config_.current_bitrate;
}

int BitrateController::compute_next_bitrate(const NetworkMetrics& metrics, int64_t now_ms) {
    if (should_scale_down(metrics)) {
        return static_cast<int>(config_.current_bitrate * config_.scale_down_factor);
    }

    if (should_scale_up(metrics)) {
        // Check cooldown
        if (now_ms - last_increase_ms_ < config_.increase_cooldown_ms) {
            return config_.current_bitrate;
        }
        return static_cast<int>(config_.current_bitrate * config_.scale_up_factor);
    }

    return config_.current_bitrate;
}

bool BitrateController::should_scale_down(const NetworkMetrics& metrics) const {
    // Packet loss > threshold
    if (metrics.packet_loss_ratio > config_.down_scale_threshold) return true;

    // High RTT
    if (metrics.rtt_ms > 300.0) return true;

    // Throughput near limit
    if (metrics.throughput_bps > 0 &&
        metrics.throughput_bps < config_.current_bitrate * 1.1) return true;

    // Average loss over history
    if (!loss_history_.empty()) {
        double avg_loss = 0;
        for (double l : loss_history_) avg_loss += l;
        avg_loss /= loss_history_.size();
        if (avg_loss > 0.03) return true;
    }

    return false;
}

bool BitrateController::should_scale_up(const NetworkMetrics& metrics) const {
    // Low loss, low RTT, high throughput
    bool good_network = metrics.packet_loss_ratio < 0.01 &&
                        metrics.rtt_ms < 100.0 &&
                        metrics.throughput_bps > config_.current_bitrate * 1.5;

    // Min duration between changes
    if (metrics.rtt_ms != 0) {  // hack: skip check if no real metrics
        int64_t now = static_cast<int64_t>(metrics.rtt_ms * 10); // fake now
        // In real impl, pass now_ms
    }

    return good_network;
}

int BitrateController::clamp_bitrate(int bitrate) const {
    return std::max(config_.min_bitrate, std::min(config_.max_bitrate, bitrate));
}

void BitrateController::force_bitrate(int bitrate) {
    config_.current_bitrate = clamp_bitrate(bitrate);
}

void BitrateController::reset() {
    config_.current_bitrate = config_.initial_bitrate;
    loss_history_.clear();
    rtt_history_.clear();
    last_change_ms_ = 0;
    last_increase_ms_ = 0;
}
