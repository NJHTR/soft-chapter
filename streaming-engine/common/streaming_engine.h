#pragma once

#include "capture/capture.h"
#include "gpu/gpu_pipeline.h"
#include "ai/ai_engine.h"
#include "beauty/beauty_pipeline.h"
#include "encoder/encoder.h"
#include "encoder/bitrate_controller.h"
#include "network/network.h"
#include "player/player.h"
#include "common/pipeline.h"
#include <memory>
#include <functional>

// Top-level streaming engine orchestrator
// Manages the entire pipeline from capture to streaming

class StreamingEngine {
public:
    StreamingEngine();
    ~StreamingEngine();

    // Lifecycle
    bool init(const PipelineConfig& config);
    bool start();
    bool stop();
    bool is_running() const;

    // Pipeline access
    VideoPipeline& pipeline() { return *pipeline_; }
    BitrateController& bitrate_controller() { return *bitrate_ctrl_; }

    // Dynamic configuration (hot-swappable)
    void set_beauty_config(const BeautyConfig& config);
    void set_bitrate(int bitrate);
    void set_encoder_config(const EncoderConfig& config);

    // Statistics
    struct EngineStats {
        double capture_fps;
        double encode_fps;
        double stream_fps;
        int64_t bytes_sent;
        double rtt_ms;
        double packet_loss;
        int current_bitrate;
        int gpu_usage_percent;
        int cpu_usage_percent;
    };
    EngineStats stats() const;

    // Events
    using StatsCallback = std::function<void(const EngineStats&)>;
    void set_stats_callback(StatsCallback cb) { stats_cb_ = std::move(cb); }

    using ErrorCallback = std::function<void(const std::string& error)>;
    void set_error_callback(ErrorCallback cb) { error_cb_ = std::move(cb); }

private:
    class Impl;
    std::unique_ptr<Impl> impl_;

    std::unique_ptr<VideoPipeline> pipeline_;
    std::unique_ptr<BitrateController> bitrate_ctrl_;
    std::unique_ptr<Encoder> encoder_;
    std::unique_ptr<NetworkStreamer> streamer_;

    StatsCallback stats_cb_;
    ErrorCallback error_cb_;

    void stats_report_loop();
    void network_adaptation_loop();
};
