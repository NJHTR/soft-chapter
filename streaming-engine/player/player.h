#pragma once

#include "common/frame.h"
#include <memory>
#include <functional>
#include <string>

enum class PlayerBackend {
    WebCodecs,   // Browser WebCodecs API (Chrome, Edge)
    MSE,         // Media Source Extensions (fallback)
    WebRTC,      // WebRTC playback
    Canvas,      // Canvas 2D rendering (base64 JPEG fallback)
    ExoPlayer,   // Android ExoPlayer
    AVPlayer,    // iOS AVPlayer
    FFmpeg,      // Desktop FFmpeg
    FlutterVideo,// Flutter video_player
};

struct PlayerConfig {
    PlayerBackend backend = PlayerBackend::WebCodecs;
    std::string stream_url;
    StreamProtocol protocol = StreamProtocol::SRT;

    // Playback settings
    int buffer_ms = 200;       // Target buffer for low latency
    int max_buffer_ms = 1000;  // Max buffer
    bool enable_hardware_decode = true;
    bool enable_auto_bitrate = true;

    // Display
    int output_width = 1920;
    int output_height = 1080;
    bool enable_hdr = false;
};

class Player {
public:
    virtual ~Player() = default;

    virtual bool init(const PlayerConfig& config) = 0;
    virtual bool play() = 0;
    virtual bool pause() = 0;
    virtual bool resume() = 0;
    virtual bool stop() = 0;
    virtual bool seek(int64_t timestamp_ms) = 0;

    virtual bool is_playing() const = 0;
    virtual int64_t current_timestamp_ms() const = 0;
    virtual double playback_speed() const = 0;
    virtual void set_playback_speed(double speed) = 0;

    // Statistics
    virtual double fps() const = 0;
    virtual double bitrate_kbps() const = 0;
    virtual int dropped_frames() const = 0;

    using FrameCallback = std::function<void(std::shared_ptr<Frame>)>;
    void set_frame_callback(FrameCallback cb) { frame_cb_ = std::move(cb); }

    using ErrorCallback = std::function<void(const std::string& error)>;
    void set_error_callback(ErrorCallback cb) { error_cb_ = std::move(cb); }

protected:
    FrameCallback frame_cb_;
    ErrorCallback error_cb_;
};

// ===== Player Backend Comparison =====
//
// Backend    | Platform    | Latency | Quality | GPU Decode
// -----------+-------------+---------+---------+-----------
// WebCodecs  | Browser     | ~100ms  | Best    | Yes
// MSE        | Browser     | ~500ms  | Good    | Yes
// WebRTC     | Cross       | ~50ms   | Good    | Yes
// Canvas     | Any         | ~300ms  | Poor    | No
// ExoPlayer  | Android     | ~200ms  | Best    | Yes
// AVPlayer   | iOS         | ~200ms  | Best    | Yes
// FFmpeg     | Desktop     | ~100ms  | Best    | Yes (Vulkan)
