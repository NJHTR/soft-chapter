#pragma once

#include "ai/ai_engine.h"

// NVIDIA TensorRT Inference Engine
// Supports all model types with FP16/INT8 optimization

class TensorRTEngine {
public:
    TensorRTEngine();
    ~TensorRTEngine();

    bool init(int device_id = 0);
    bool build_engine(const AIModelConfig& config,
                      const void* model_data, size_t model_size);
    bool infer(void* input_gpu, size_t input_size,
               void* output_gpu, size_t output_size,
               void* cuda_stream = nullptr);
    void shutdown();

    // Bind CUDA texture for direct GPU input
    bool bind_cuda_texture(void* cuda_resource, int width, int height);

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

// ===== TensorRT Optimization Tips =====
//
// 1. Use FP16 for 2x speedup vs FP32 with minimal quality loss
// 2. Use INT8 for 4x speedup with calibration
// 3. Use dynamic shapes for variable input sizes
// 4. Use CUDA streams for async inference
// 5. Use multi-stream for concurrent model execution
// 6. Use DLA (Deep Learning Accelerator) on Jetson
