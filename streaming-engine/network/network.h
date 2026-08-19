#pragma once

#include "common/frame.h"
#include "encoder/encoder.h"
#include "encoder/bitrate_controller.h"
#include <memory>
#include <functional>

enum class StreamProtocol {
    RTMP,      // Traditional RTMP (fallback)
    SRT,       // Reliable, low-latency (TikTok primary)
    WebRTC,    // Ultra-low latency (<300ms)
    WHIP,      // WebRTC ingest protocol
    RIST,      // Reliable Internet Stream Transport
    QUIC,      // HTTP/3 based
    HTTP_FLV,  // HTTP-FLV (Chinese market)
    LL_HLS,    // Low-Latency HLS
};

struct StreamConfig {
    StreamProtocol protocol = StreamProtocol::SRT;
    std::string server_url;
    int server_port = 0;
    std::string stream_key;
    std::string app_name = "live";

    // SRT specific
    int srt_latency_ms = 120;    // SRT latency target
    int srt_tsbpd_ms = 200;     // Timestamp-based packet delivery
    bool srt_encryption = false;
    std::string srt_passphrase;

    // WebRTC specific
    std::string stun_server = "stun:stun.l.google.com:19302";
    std::vector<std::string> turn_servers;
    bool ice_lite = false;

    // RTMP specific
    std::string rtmp_url;
    bool enable_auth = false;
    std::string username;
    std::string password;
};

// ===== Protocol Comparison =====
//
// Protocol | Latency | Quality | Reliability | Complexity
// ---------+---------+---------+-------------+-----------
// SRT      | ~200ms  | Best    | Excellent   | Low
// WebRTC   | ~100ms  | Good    | Good        | High
// RTMP     | ~3s     | Good    | Poor        | Low
// WHIP     | ~100ms  | Good    | Good        | Medium
// QUIC     | ~150ms  | Best    | Good        | High
// RIST     | ~200ms  | Good    | Excellent   | Medium
// LL-HLS   | ~3s     | Best    | Excellent   | Low
//
// TikTok uses: SRT for primary ingest, WebRTC for ultra-low-latency,
//             RTMP for legacy compatibility

class NetworkStreamer {
public:
    virtual ~NetworkStreamer() = default;

    virtual bool init(const StreamConfig& config) = 0;
    virtual bool connect() = 0;
    virtual bool disconnect() = 0;
    virtual bool send_packet(const uint8_t* data, size_t size,
                             int64_t timestamp, bool is_keyframe) = 0;

    virtual bool is_connected() const = 0;
    virtual StreamProtocol protocol() const = 0;
    virtual double rtt_ms() const = 0;
    virtual double packet_loss() const = 0;

    using ConnectionCallback = std::function<void(bool connected)>;
    using ErrorCallback = std::function<void(const std::string& error)>;

    void set_connection_callback(ConnectionCallback cb) { conn_cb_ = std::move(cb); }
    void set_error_callback(ErrorCallback cb) { error_cb_ = std::move(cb); }

protected:
    ConnectionCallback conn_cb_;
    ErrorCallback error_cb_;
};

struct NetworkPipeConfig {
    StreamConfig stream_config;
    EncoderConfig encoder_config;
    BitrateConfig bitrate_config;
};

class NetworkPipe {
public:
    NetworkPipe();
    ~NetworkPipe();

    bool init(const NetworkPipeConfig& config);
    bool push_encoded_packet(const uint8_t* data, size_t size,
                             int64_t timestamp, bool is_keyframe);
    void shutdown();

    NetworkStreamer* streamer() const;
    BitrateController* bitrate_controller() const;

private:
    std::unique_ptr<NetworkStreamer> streamer_;
    std::unique_ptr<BitrateController> bitrate_ctrl_;
    StreamConfig config_;
    bool monitor_running_ = false;

    void network_monitor_loop();
};
