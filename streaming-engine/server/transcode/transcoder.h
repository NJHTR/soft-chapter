#pragma once

#include "common/frame.h"
#include "encoder/encoder.h"
#include <memory>
#include <vector>
#include <functional>

// Adaptive Bitrate Transcoding
// Converts a single high-bitrate stream into multiple quality tiers

struct TranscodeConfig {
    int input_width = 1920;
    int input_height = 1080;
    int input_fps = 60;
    int input_bitrate = 8000000;

    // ABR ladder (same as TikTok)
    struct ABRTier {
        int width;
        int height;
        int fps;
        int bitrate;
        Codec codec = Codec::AV1;
    };

    std::vector<ABRTier> tiers = {
        {1920, 1080, 60, 8000000, Codec::AV1},   // 1080P60
        {1280, 720,  60, 4000000, Codec::AV1},    // 720P60
        {854,  480,  30, 1500000, Codec::H265},   // 480P30
        {640,  360,  30,  800000, Codec::H265},   // 360P30
    };
};

class Transcoder {
public:
    Transcoder();
    ~Transcoder();

    bool init(const TranscodeConfig& config);
    bool transcode(std::shared_ptr<Frame> frame);
    void shutdown();

    using ABRCallback = std::function<void(int tier_index,
                                           std::vector<uint8_t> packet,
                                           int64_t timestamp,
                                           bool is_keyframe)>;
    void set_output_callback(ABRCallback cb) { output_cb_ = std::move(cb); }

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
    TranscodeConfig config_;
    ABRCallback output_cb_;
    std::vector<std::unique_ptr<Encoder>> encoders_;
};
