#pragma once

#include "capture/capture.h"

// Cross-platform camera capture with oversampling
//
// Implementation strategy per platform:
//   Windows: MediaFoundation + DirectShow fallback
//   macOS:   AVFoundation
//   Linux:   V4L2
//   Android: Camera2 API
//   iOS:     AVFoundation
//
// Oversampling pipeline:
//   1. Request 4K (3840x2160) from camera
//   2. Receive YUV/NV12 frames
//   3. Upload to GPU as texture
//   4. Apply GPU downsample shader (bilinear/lanczos)
//   5. Output 1920x1080 frame in GPU memory
//   (zero CPU copy)

class CameraCapture : public Capture {
public:
    CameraCapture();
    ~CameraCapture() override;

    bool init(const CaptureConfig& config) override;
    bool start() override;
    bool stop() override;
    bool read_frame(std::shared_ptr<Frame>& frame) override;
    void set_frame_callback(FrameCallback cb) override;

    CaptureSource source() const override { return CaptureSource::USB_Camera; }
    std::string device_name() const override;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
