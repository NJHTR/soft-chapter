#pragma once

#include "network/muxer.h"
#include "common/frame.h"
#include <memory>
#include <string>
#include <functional>

// RTMP Streamer
// Streams FLV-encapsulated media to an RTMP endpoint
// Supports: RTMP, RTMPS, RTMPT

struct RTMPConfig {
    std::string url;          // rtmp://ingest.example.com/live/streamkey
    int reconnect_interval = 3000;   // ms between reconnect attempts
    int max_reconnects = 10;
    int timeout_ms = 5000;    // socket timeout
    bool use_ssl = false;
    std::string proxy;        // optional SOCKS5 proxy
};

class RTMPStreamer {
public:
    RTMPStreamer();
    ~RTMPStreamer();

    bool init(const RTMPConfig& config);
    bool connect();
    bool disconnect();
    bool send_packet(const EncodedPacket& packet);
    bool is_connected() const;

    void set_status_callback(std::function<void(const std::string& status)> cb);
    std::string last_error() const;

    // Stats
    struct RTMPStats {
        int64_t bytes_sent = 0;
        int64_t packets_sent = 0;
        int64_t connect_time_ms = 0;
        int reconnect_count = 0;
        double bitrate_kbps = 0.0;
    };
    RTMPStats get_stats() const;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
