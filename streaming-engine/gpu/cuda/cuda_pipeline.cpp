#include "gpu/cuda/cuda_pipeline.h"
#include <cuda.h>
#include <cuda_runtime.h>
#include <cuda_gl_interop.h>
#include <iostream>

// ===== CUDA Memory Pool =====

class CUDAMemoryPool::Impl {
public:
    struct PooledFrame {
        cudaArray_t arr = nullptr;
        cudaSurfaceObject_t surf = 0;
        int width, height;
        PixelFormat format;
        bool in_use = false;
    };

    GPUConfig config_;
    std::vector<PooledFrame> pool_;
    cudaStream_t stream_ = nullptr;

    bool init(const GPUConfig& config) {
        config_ = config;
        cudaSetDevice(config.device_id);
        cudaStreamCreateWithFlags(&stream_, cudaStreamNonBlocking);
        return stream_ != nullptr;
    }

    cudaArray_t allocate_array(int w, int h, PixelFormat fmt) {
        cudaChannelFormatDesc desc;
        switch (fmt) {
            case PixelFormat::NV12:
                desc = cudaCreateChannelDesc(8, 8, 0, 0, cudaChannelFormatKindUnsigned);
                break;
            case PixelFormat::RGBA:
                desc = cudaCreateChannelDesc(8, 8, 8, 8, cudaChannelFormatKindUnsigned);
                break;
            case PixelFormat::P010:
                desc = cudaCreateChannelDesc(16, 16, 0, 0, cudaChannelFormatKindUnsigned);
                break;
            default:
                desc = cudaCreateChannelDesc(8, 8, 8, 8, cudaChannelFormatKindUnsigned);
        }

        cudaArray_t arr;
        cudaMallocArray(&arr, &desc, w, h, cudaArraySurfaceLoadStore);
        return arr;
    }

    void shutdown() {
        for (auto& f : pool_) {
            if (f.surf) cudaDestroySurfaceObject(f.surf);
            if (f.arr) cudaFreeArray(f.arr);
        }
        pool_.clear();
        if (stream_) cudaStreamDestroy(stream_);
    }
};

CUDAMemoryPool::CUDAMemoryPool() : impl_(std::make_unique<Impl>()) {}
CUDAMemoryPool::~CUDAMemoryPool() = default;

bool CUDAMemoryPool::init(const GPUConfig& config) { return impl_->init(config); }
void CUDAMemoryPool::shutdown() { impl_->shutdown(); }

// ===== CUDA Oversampler (4K → 1080P Lanczos) =====

extern "C" {
    // CUDA kernel defined in cuda_oversample.cu
    void launch_oversample_lanczos3(
        cudaSurfaceObject_t input, int in_w, int in_h,
        cudaSurfaceObject_t output, int out_w, int out_h,
        cudaStream_t stream);
}

bool CUDADownsampler::init(GPUApi api, int device_id) {
    if (api != GPUApi::CUDA) return false;
    api_ = api;
    device_id_ = device_id;
    cudaSetDevice(device_id);
    cudaStreamCreate(&stream_);
    return true;
}

// ===== CUDA NPP-based Color Conversion =====

bool CUDAColorConverter::init(GPUApi api, int device_id) {
    api_ = api;
    device_id_ = device_id;
    cudaSetDevice(device_id);
    return true;
}
