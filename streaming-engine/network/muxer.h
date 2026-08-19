#pragma once

#include "common/frame.h"
#include "encoder/audio_encoder.h"
#include "encoder/encoder.h"
#include <memory>
#include <vector>
#include <functional>
#include <string>

// Media Muxer
// Multiplexes encoded video (H.264/H.265/AV1) and audio (AAC/Opus)
// into a container format for streaming or recording.
// Supports: FLV (RTMP), MP4, TS (SRT/HLS)

enum class ContainerFormat {
    FLV,   // RTMP streaming
    MP4,   // Recording (fragmented MP4 for live)
    MPEGTS,// MPEG-TS for SRT/HLS
    WebM,  // WebRTC
};

struct MuxerConfig {
    ContainerFormat format = ContainerFormat::FLV;
    int video_timescale = 90000;  // 90kHz for TS
    int audio_timescale = 48000;  // 48kHz
    int fragment_duration = 2000; // 2s fragments for live MP4
    std::string output_url;        // rtmp://, srt://, file://
};

struct EncodedPacket {
    std::vector<uint8_t> data;
    int64_t pts;     // presentation timestamp (microseconds)
    int64_t dts;     // decode timestamp (microseconds)
    bool is_keyframe = false;
    Codec video_codec = Codec::H264;
    AudioCodec audio_codec = AudioCodec::AAC;
    bool is_video = true;
};

class Muxer {
public:
    virtual ~Muxer() = default;
    virtual bool init(const MuxerConfig& config) = 0;
    virtual bool write_header() = 0;
    virtual bool write_packet(const EncodedPacket& packet) = 0;
    virtual bool write_trailer() = 0;
    virtual void shutdown() = 0;

    virtual bool is_initialized() const = 0;

    using PacketCallback = std::function<void(std::vector<uint8_t> data,
                                               int64_t dts, bool is_keyframe)>;
    virtual void set_packet_callback(PacketCallback cb) = 0;
};

// FFmpeg-based muxer
std::unique_ptr<Muxer> create_muxer(const MuxerConfig& config);

// Pure FLV muxer (no FFmpeg dependency, for RTMP)
std::unique_ptr<Muxer> create_flv_muxer();
