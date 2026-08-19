#pragma once

#include "common/frame.h"
#include <memory>
#include <vector>
#include <functional>
#include <string>

enum class EncoderType {
    NVENC,       // NVIDIA GPU encoder
    x264,        // Software H.264
    x265,        // Software H.265
    SVT_AV1,     // Intel AV1 software
    QuickSync,   // Intel QuickSync
    AMF,         // AMD AMF
    VideoToolbox,// Apple VideoToolbox (macOS/iOS)
    MediaCodec,  // Android MediaCodec
};

enum class Codec {
    H264,
    H265,
    AV1,
};

struct EncoderConfig {
    EncoderType type = EncoderType::NVENC;
    Codec codec = Codec::AV1;
    int width = 1920;
    int height = 1080;
    int fps = 60;
    int bitrate = 4000000;      // 4 Mbps target
    int max_bitrate = 8000000;  // 8 Mbps max
    int min_bitrate = 500000;   // 500 Kbps min
    int gop_size = 120;         // Keyframe interval
    int b_frames = 2;
    bool enable_hdr = false;
    bool enable_10bit = false;
    int cq_level = 23;          // CRF/CQ quality level

    // NVENC specific
    int nvenc_preset = 7;       // P7 (slowest = best quality)
    bool nvenc_lookahead = true;
    int nvenc_lookahead_depth = 32;

    // x264 specific
    std::string x264_preset = "medium";
    std::string x264_tune = "zerolatency";
};

class Encoder {
public:
    virtual ~Encoder() = default;

    virtual bool init(const EncoderConfig& config) = 0;
    virtual bool encode(std::shared_ptr<Frame> frame,
                        std::vector<uint8_t>& packet) = 0;
    virtual bool flush(std::vector<uint8_t>& packet) = 0;
    virtual void shutdown() = 0;

    // Dynamic bitrate adjustment
    virtual void set_bitrate(int bitrate) = 0;
    virtual void request_keyframe() = 0;

    using EncodedCallback = std::function<void(std::vector<uint8_t> packet,
                                                int64_t timestamp,
                                                bool is_keyframe)>;
    virtual void set_output_callback(EncodedCallback cb) = 0;

    virtual EncoderType type() const = 0;
    virtual Codec codec() const = 0;
    virtual std::string codec_name() const = 0;
};

std::unique_ptr<Encoder> create_encoder(const EncoderConfig& config);
