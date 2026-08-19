#pragma once

#include "common/frame.h"
#include <memory>
#include <string>
#include <functional>

enum class CaptureSource {
    USB_Camera,
    DSLR,
    HDMI_Capture,
    OBS,
    FFmpeg,
    MediaFoundation,  // Windows
    DirectShow,       // Windows
    AVFoundation,     // macOS/iOS
    V4L2,             // Linux
    Camera2,          // Android
    Screen,
};

struct CaptureConfig {
    CaptureSource source = CaptureSource::USB_Camera;
    std::string device_path;     // "/dev/video0", "USB\\VID_..."
    int width = 3840;            // 4K capture
    int height = 2160;
    int fps = 60;
    bool use_oversampling = true;
    int oversample_factor = 2;   // 4K → oversample → 1080P

    // DSLR specific
    std::string dslr_ip;
    int dslr_port = 0;

    // OBS specific
    std::string obs_websocket_url = "ws://localhost:4455";

    // FFmpeg specific
    std::string ffmpeg_input_url;
    std::string ffmpeg_format = "dshow";
};

// Oversampling: capture at 4K, GPU downsample to 1080P
// This provides superior quality vs native 1080P due to:
//   - More spatial information for anti-aliasing
//   - Better sub-pixel rendering
//   - Reduced moire patterns
//   - Improved SNR (signal-to-noise ratio)

class Capture {
public:
    virtual ~Capture() = default;

    virtual bool init(const CaptureConfig& config) = 0;
    virtual bool start() = 0;
    virtual bool stop() = 0;
    virtual bool read_frame(std::shared_ptr<Frame>& frame) = 0;

    using FrameCallback = std::function<void(std::shared_ptr<Frame>)>;
    virtual void set_frame_callback(FrameCallback cb) = 0;

    virtual CaptureSource source() const = 0;
    virtual std::string device_name() const = 0;
};

std::unique_ptr<Capture> create_capture(const CaptureConfig& config);
