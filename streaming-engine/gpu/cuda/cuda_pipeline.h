#pragma once

#include "gpu/gpu_pipeline.h"

// CUDA-accelerated memory pool with zero-copy
class CUDAMemoryPool {
public:
    CUDAMemoryPool();
    ~CUDAMemoryPool();

    bool init(const GPUConfig& config);
    void shutdown();

    class Impl;
    std::unique_ptr<Impl> impl_;
};

// CUDA Lanczos downsampler (4K → 1080P)
class CUDADownsampler : public GPUPipelineNode {
public:
    bool init(GPUApi api, int device_id) override;
    bool process(std::shared_ptr<Frame> input,
                 std::shared_ptr<Frame>& output) override;
    void shutdown() override;
    void sync() override;

private:
    void* lanczos_kernel_ = nullptr;
    cudaStream_t stream_ = nullptr;
};

// CUDA color converter using NPP
class CUDAColorConverter : public GPUPipelineNode {
public:
    bool init(GPUApi api, int device_id) override;
    bool process(std::shared_ptr<Frame> input,
                 std::shared_ptr<Frame>& output) override;
    void shutdown() override;
    void sync() override {}
};
