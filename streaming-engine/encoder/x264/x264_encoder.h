#pragma once

#include "encoder/encoder.h"

// x264/x265 software encoder (fallback when no GPU encoder available)
// Uses libx264/libx265 via FFmpeg libavcodec

class X264Encoder : public Encoder {
public:
    X264Encoder();
    ~X264Encoder() override;

    bool init(const EncoderConfig& config) override;
    bool encode(std::shared_ptr<Frame> frame,
                std::vector<uint8_t>& packet) override;
    bool flush(std::vector<uint8_t>& packet) override;
    void shutdown() override;

    void set_bitrate(int bitrate) override;
    void request_keyframe() override;
    void set_output_callback(EncodedCallback cb) override;

    EncoderType type() const override { return EncoderType::x264; }
    Codec codec() const override { return Codec::H264; }
    std::string codec_name() const override { return "libx264"; }

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

class X265Encoder : public Encoder {
public:
    X265Encoder();
    ~X265Encoder() override;

    bool init(const EncoderConfig& config) override;
    bool encode(std::shared_ptr<Frame> frame,
                std::vector<uint8_t>& packet) override;
    bool flush(std::vector<uint8_t>& packet) override;
    void shutdown() override;

    void set_bitrate(int bitrate) override;
    void request_keyframe() override;
    void set_output_callback(EncodedCallback cb) override;

    EncoderType type() const override { return EncoderType::x265; }
    Codec codec() const override { return Codec::H265; }
    std::string codec_name() const override { return "libx265"; }

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
