#pragma once

#include "common/frame.h"
#include <memory>
#include <vector>
#include <functional>

enum class GPUApi {
    CUDA,
    OpenGL,
    Vulkan,
    Metal,
    OpenCL,
};

struct GPUConfig {
    GPUApi api = GPUApi::CUDA;
    int device_id = 0;           // GPU device index
    int max_texture_size = 4096;
    bool enable_zero_copy = true;
    bool use_pinned_memory = true;
    int queue_depth = 3;         // triple buffering
};

// GPU Memory Pool for zero-copy frame management
class GPUMemoryPool {
public:
    GPUMemoryPool();
    ~GPUMemoryPool();

    bool init(const GPUConfig& config);
    void shutdown();

    // Allocate GPU frame with specified format
    std::shared_ptr<Frame> allocate_frame(int width, int height, PixelFormat fmt);

    // Recycle frame back to pool
    void recycle(std::shared_ptr<Frame> frame);

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

// GPU Pipeline Node - base for all GPU operations
class GPUPipelineNode {
public:
    virtual ~GPUPipelineNode() = default;

    virtual bool init(GPUApi api, int device_id) = 0;
    virtual bool process(std::shared_ptr<Frame> input,
                         std::shared_ptr<Frame>& output) = 0;
    virtual void shutdown() = 0;

    // GPU synchronization
    virtual void sync() = 0;

protected:
    GPUApi api_;
    int device_id_;
};

// Oversampling: 4K → 1080P using GPU shader
// Uses Lanczos or Bicubic interpolation for maximum quality
class GPUOversampler : public GPUPipelineNode {
public:
    bool init(GPUApi api, int device_id) override;
    bool process(std::shared_ptr<Frame> input,
                 std::shared_ptr<Frame>& output) override;
    void shutdown() override;
    void sync() override;

    void set_output_size(int w, int h) { out_w_ = w; out_h_ = h; }

private:
    int out_w_ = 1920;
    int out_h_ = 1080;
    void* shader_ = nullptr;
};

// GPU Color Converter (NV12/YUV ↔ RGBA, 8-bit ↔ 10-bit, etc.)
class GPUColorConverter : public GPUPipelineNode {
public:
    bool init(GPUApi api, int device_id) override;
    bool process(std::shared_ptr<Frame> input,
                 std::shared_ptr<Frame>& output) override;
    void shutdown() override;
    void sync() override;

    void set_output_format(PixelFormat fmt) { out_fmt_ = fmt; }

private:
    PixelFormat out_fmt_ = PixelFormat::RGBA;
};
