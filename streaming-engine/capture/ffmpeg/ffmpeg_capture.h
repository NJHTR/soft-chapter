#pragma once

#include "capture/capture.h"

// FFmpeg-based capture for any input source
// Supports: USB cameras, RTSP/IP cameras, HDMI capture cards,
//           screen capture, OBS virtual camera

class FFmpegCapture : public Capture {
public:
    FFmpegCapture();
    ~FFmpegCapture() override;

    bool init(const CaptureConfig& config) override;
    bool start() override;
    bool stop() override;
    bool read_frame(std::shared_ptr<Frame>& frame) override;
    void set_frame_callback(FrameCallback cb) override;

    CaptureSource source() const override { return CaptureSource::FFmpeg; }
    std::string device_name() const override;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
