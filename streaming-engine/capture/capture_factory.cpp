#include "capture/capture.h"
#include "capture/camera/camera_capture.h"
#include "capture/ffmpeg/ffmpeg_capture.h"
#include <iostream>

std::unique_ptr<Capture> create_capture(const CaptureConfig& config) {
    switch (config.source) {
        case CaptureSource::USB_Camera:
        case CaptureSource::DSLR:
        case CaptureSource::HDMI_Capture:
        case CaptureSource::Screen:
            return std::make_unique<CameraCapture>();

        case CaptureSource::OBS:
        case CaptureSource::FFmpeg:
            return std::make_unique<FFmpegCapture>();

        case CaptureSource::MediaFoundation:
        case CaptureSource::DirectShow:
        case CaptureSource::AVFoundation:
        case CaptureSource::V4L2:
        case CaptureSource::Camera2:
            // Platform-specific: each uses CameraCapture with different backends
            return std::make_unique<CameraCapture>();

        default:
            std::cerr << "Unknown capture source: " << static_cast<int>(config.source) << std::endl;
            return nullptr;
    }
}
