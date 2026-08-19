#pragma once

#include "network/network.h"
#include "encoder/encoder.h"
#include <memory>
#include <string>

enum class StreamServerType {
    SRS,               // SRS (Simple Realtime Server) - recommended
    MediaMTX,          // MediaMTX (formerly rtsp-simple-server)
    ZLMediaKit,        // ZLMediaKit
    LiveKit,           // LiveKit (WebRTC native)
    Janus,             // Janus WebRTC Gateway
    mediasoup,         // mediasoup (WebRTC)
    OvenMediaEngine,   // OvenMediaEngine
    NginxRTMP,         // Nginx-RTMP module
};

// ===== Server Comparison =====
//
// Server      | Protocol | Latency | Scale | Ease
// ------------+----------+---------+-------+-------
// SRS 6.0     | SRT/RTC  | 100ms   | 100K  | Easy
// MediaMTX    | SRT/RTS  | 100ms   | 10K   | Easy
// LiveKit     | WebRTC   | 50ms    | 100K  | Medium
// Janus       | WebRTC   | 50ms    | 50K   | Hard
// mediasoup   | WebRTC   | 50ms    | 50K   | Hard
// OME         | SRT/WRS  | 100ms   | 100K  | Medium
// ZLMediaKit  | RTMP/SRT | 200ms   | 50K   | Medium
//
// Recommendation: SRS 6.0 as primary, MediaMTX as lightweight alternative

class StreamServer {
public:
    StreamServer();
    ~StreamServer();

    bool init(StreamServerType type, const std::string& config_path);
    bool start();
    bool stop();
    bool restart();
    bool is_running() const;

    // Ingest API
    bool create_stream(const std::string& stream_key,
                       const EncoderConfig& config);
    bool remove_stream(const std::string& stream_key);

    // ABR (Adaptive Bitrate)
    struct ABRLayer {
        int width;
        int height;
        int bitrate;
        std::string codec;
    };
    bool configure_abr(const std::vector<ABRLayer>& layers);

    // Transcode
    bool enable_transcoding(bool enable);

    // Statistics
    struct ServerStats {
        int64_t bytes_in;
        int64_t bytes_out;
        int active_streams;
        int total_viewers;
        double cpu_usage;
        double memory_mb;
    };
    ServerStats stats() const;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
