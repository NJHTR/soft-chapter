#pragma once

#include "common/frame.h"
#include <memory>
#include <string>
#include <functional>

enum class AIBackend {
    TensorRT,   // NVIDIA, fastest, needs GPU
    ONNX,       // Cross-platform, good perf
    NCNN,       // Mobile-optimized
    MNN,        // Mobile-optimized (Alibaba)
    OpenVINO,   // Intel CPU/GPU optimized
};

enum class AIModelType {
    Denoise,
    SuperResolution,
    FaceDetection,
    FaceLandmark,
    Segmentation,
    Interpolation,
};

struct AIModelConfig {
    AIModelType type;
    AIBackend backend = AIBackend::TensorRT;
    std::string model_path;
    int input_width = 1920;
    int input_height = 1080;
    int batch_size = 1;
    bool use_fp16 = true;     // FP16 inference
    bool use_int8 = false;    // INT8 quantization
    int max_gpu_memory_mb = 1024;
    int num_streams = 2;      // Async inference streams
};

class AIModel {
public:
    virtual ~AIModel() = default;
    virtual bool load(const AIModelConfig& config) = 0;
    virtual bool infer(std::shared_ptr<Frame> input,
                       std::shared_ptr<Frame>& output) = 0;
    virtual void shutdown() = 0;
    virtual AIModelType type() const = 0;
    virtual AIBackend backend() const = 0;
};

// ===== AI Model Recommendations =====
//
// DENOISE:
//   FastDVDNet  - Best quality, ~30 FPS on RTX4090, ~2GB VRAM
//   NAFNet      - Lighter, ~45 FPS, ~1.2GB VRAM
//   BasicVSR++  - Video-based, best temporal stability, ~20 FPS
//
// SUPER RESOLUTION:
//   RealESRGAN  - Best quality, ~15 FPS (720P→1080P), ~3GB VRAM
//   SwinIR      - Good quality, ~25 FPS, ~2GB VRAM
//   FSRCNN      - Fast, ~60 FPS, ~500MB VRAM
//
// FACE DETECTION:
//   MediaPipe   - ~200 FPS, lightweight
//   InsightFace - ~100 FPS, high accuracy
//   YOLO Face   - ~150 FPS, good accuracy
//
// INTERPOLATION:
//   RIFE        - ~30 FPS, 2x frame interpolation

// AI Model factory (defined in ai_factory.cpp)
std::unique_ptr<AIModel> create_ai_model(const std::string& model_name,
                                          AIBackend preferred_backend = AIBackend::TensorRT);
