#pragma once

#include "frame.h"
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

struct PipelineConfig {
    int input_width = 3840;   // 4K capture
    int input_height = 2160;
    int output_width = 1920;  // 1080P output
    int output_height = 1080;
    int fps = 60;
    bool enable_denoise = true;
    bool enable_beauty = true;
    bool enable_super_res = true;
    bool enable_hdr = true;
    bool enable_sharpen = true;
    std::string color_lut_path;
    std::string beauty_model = "mediapipe";
    std::string denoise_model = "fastdvdnet";
    std::string super_res_model = "realesrgan";
    std::string encoder = "nvenc";
    std::string encoder_codec = "av1";
    int bitrate = 4000000;     // 4 Mbps
    int max_bitrate = 8000000; // 8 Mbps
    int min_bitrate = 500000;  // 500 Kbps
};

enum class PipelineStage {
    Capture,
    Denoise,
    Beauty,
    SuperResolution,
    ColorCorrection,
    HDR,
    Sharpen,
    Encode,
    Stream,
};

class PipelineNode {
public:
    virtual ~PipelineNode() = default;
    virtual std::string name() const = 0;
    virtual PipelineStage stage() const = 0;
    virtual bool init(const PipelineConfig& config) = 0;
    virtual bool process(std::shared_ptr<Frame> input, std::shared_ptr<Frame>& output) = 0;
    virtual void shutdown() = 0;
};

class VideoPipeline {
public:
    VideoPipeline();
    ~VideoPipeline();

    bool init(const PipelineConfig& config);
    bool push_frame(std::shared_ptr<Frame> frame);
    void shutdown();

    using FrameCallback = std::function<void(std::shared_ptr<Frame>)>;
    void set_output_callback(FrameCallback cb) { output_cb_ = std::move(cb); }

    PipelineConfig& config() { return config_; }

private:
    PipelineConfig config_;
    std::vector<std::unique_ptr<PipelineNode>> nodes_;
    FrameCallback output_cb_;
    bool running_ = false;
};
