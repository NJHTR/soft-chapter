#pragma once

#include "encoder/encoder.h"

// NVIDIA NVENC Hardware Encoder
// Supports: H.264, H.265 (HEVC), AV1 (ADA Lovelace+)
// Zero-copy from CUDA texture to encoder input

class NvEncEncoder : public Encoder {
public:
    NvEncEncoder();
    ~NvEncEncoder() override;

    bool init(const EncoderConfig& config) override;
    bool encode(std::shared_ptr<Frame> frame,
                std::vector<uint8_t>& packet) override;
    bool flush(std::vector<uint8_t>& packet) override;
    void shutdown() override;

    void set_bitrate(int bitrate) override;
    void request_keyframe() override;
    void set_output_callback(EncodedCallback cb) override;

    EncoderType type() const override { return EncoderType::NVENC; }
    Codec codec() const override;
    std::string codec_name() const override;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

// ===== NVENC Quality Comparison =====
//
// Codec | Quality  | Bitrate  | GPU Usage | Latency
// ------+----------+----------+-----------+--------
// AV1   | Best     | Lowest   | ~40%      | Low
// H.265 | Good     | Medium   | ~25%      | Lowest
// H.264 | Decent   | Highest  | ~15%      | Lowest
//
// TikTok uses NVENC AV1 for primary stream,
// NVENC H.265 for fallback compatibility
