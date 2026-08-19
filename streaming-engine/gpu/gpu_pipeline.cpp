#include "gpu/gpu_pipeline.h"
#include <iostream>
#include <cstring>
#include <algorithm>

// ===== GPU Oversampler =====

bool GPUOversampler::init(GPUApi api, int device_id) {
    api_ = api;
    device_id_ = device_id;

    if (api == GPUApi::CUDA) {
#ifdef ENABLE_CUDA
        cudaSetDevice(device_id);
        cudaStreamCreate(reinterpret_cast<cudaStream_t*>(&shader_));
        return shader_ != nullptr;
#else
        std::cerr << "CUDA not enabled in this build" << std::endl;
        return false;
#endif
    }

    std::cout << "GPUOversampler initialized: "
              << out_w_ << "x" << out_h_
              << " from 4K input" << std::endl;
    return true;
}

bool GPUOversampler::process(std::shared_ptr<Frame> input,
                              std::shared_ptr<Frame>& output) {
    if (!input) return false;

    // Create output frame if needed
    if (!output) {
        output = Frame::create_cpu(out_w_, out_h_, PixelFormat::RGBA);
    }

    auto& in_buf = input->buffer();
    auto& out_buf = output->buffer();

    if (api_ == GPUApi::CUDA) {
        // CUDA path: launch Lanczos3 kernel
        // Input is already GPU texture from capture
#ifdef ENABLE_CUDA
        if (in_buf.memory == MemoryType::CUDA && out_buf.memory == MemoryType::CUDA) {
            launch_oversample_lanczos3(
                static_cast<cudaArray_t>(in_buf.gpu.cuda.cuda_resource),
                in_buf.width, in_buf.height,
                reinterpret_cast<float4*>(out_buf.data),
                out_w_, out_h_,
                reinterpret_cast<cudaStream_t>(shader_));
        }
#endif
    } else {
        // CPU fallback: simple bilinear (for testing)
        float scale_x = static_cast<float>(out_w_) / in_buf.width;
        float scale_y = static_cast<float>(out_h_) / in_buf.height;

        for (int y = 0; y < out_h_; y++) {
            for (int x = 0; x < out_w_; x++) {
                float src_x = (x + 0.5f) / scale_x - 0.5f;
                float src_y = (y + 0.5f) / scale_y - 0.5f;

                int ix = std::max(0, std::min(in_buf.width - 2, static_cast<int>(src_x)));
                int iy = std::max(0, std::min(in_buf.height - 2, static_cast<int>(src_y)));
                float fx = src_x - ix;
                float fy = src_y - iy;

                uint8_t* src = in_buf.data;
                uint8_t* dst = out_buf.data;
                int src_stride = in_buf.stride[0];
                int dst_stride = out_buf.stride[0];

                for (int c = 0; c < 4; c++) {
                    float p00 = src[iy * src_stride + ix * 4 + c];
                    float p10 = src[iy * src_stride + (ix + 1) * 4 + c];
                    float p01 = src[(iy + 1) * src_stride + ix * 4 + c];
                    float p11 = src[(iy + 1) * src_stride + (ix + 1) * 4 + c];

                    float v = (1 - fx) * (1 - fy) * p00 +
                               fx * (1 - fy) * p10 +
                               (1 - fx) * fy * p01 +
                               fx * fy * p11;

                    dst[y * dst_stride + x * 4 + c] = static_cast<uint8_t>(v);
                }
            }
        }
    }

    out_buf.timestamp = in_buf.timestamp;
    out_buf.frame_index = in_buf.frame_index;
    return true;
}

void GPUOversampler::shutdown() {
#ifdef ENABLE_CUDA
    if (shader_ && api_ == GPUApi::CUDA) {
        cudaStreamDestroy(reinterpret_cast<cudaStream_t>(shader_));
    }
#endif
    shader_ = nullptr;
}

void GPUOversampler::sync() {
#ifdef ENABLE_CUDA
    if (shader_ && api_ == GPUApi::CUDA) {
        cudaStreamSynchronize(reinterpret_cast<cudaStream_t>(shader_));
    }
#endif
}

// ===== GPU Color Converter =====

bool GPUColorConverter::init(GPUApi api, int device_id) {
    api_ = api;
    device_id_ = device_id;
    return true;
}

bool GPUColorConverter::process(std::shared_ptr<Frame> input,
                                 std::shared_ptr<Frame>& output) {
    if (!input) return false;

    if (!output) {
        output = Frame::create_cpu(input->buffer().width,
                                    input->buffer().height,
                                    out_fmt_);
    }

    auto& in_buf = input->buffer();
    auto& out_buf = output->buffer();

    // NV12 → RGBA conversion (CPU fallback)
    if (in_buf.format == PixelFormat::NV12 && out_fmt_ == PixelFormat::RGBA) {
        int w = in_buf.width;
        int h = in_buf.height;

        const uint8_t* y_plane = in_buf.data;
        const uint8_t* uv_plane = in_buf.data + w * h;
        uint8_t* rgba = out_buf.data;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int yi = y * w + x;
                int uvi = (y / 2) * w + (x / 2) * 2;
                int Y = y_plane[yi];
                int U = uv_plane[uvi] - 128;
                int V = uv_plane[uvi + 1] - 128;

                int R = Y + (359 * V) / 256;
                int G = Y - (88 * U - 183 * V) / 256;
                int B = Y + (454 * U) / 256;

                int offset = y * out_buf.stride[0] + x * 4;
                rgba[offset]     = std::clamp(R, 0, 255);
                rgba[offset + 1] = std::clamp(G, 0, 255);
                rgba[offset + 2] = std::clamp(B, 0, 255);
                rgba[offset + 3] = 255;
            }
        }
    }

    out_buf.timestamp = in_buf.timestamp;
    return true;
}

void GPUColorConverter::sync() {}
void GPUColorConverter::shutdown() {}

// ===== GPUMemoryPool =====

class GPUMemoryPool::Impl {
public:
    GPUConfig config_;
    std::vector<std::shared_ptr<Frame>> pool_;

    bool init(const GPUConfig& config) {
        config_ = config;
        return true;
    }

    void shutdown() {
        pool_.clear();
    }
};

GPUMemoryPool::GPUMemoryPool() : impl_(std::make_unique<Impl>()) {}
GPUMemoryPool::~GPUMemoryPool() = default;

bool GPUMemoryPool::init(const GPUConfig& config) { return impl_->init(config); }
void GPUMemoryPool::shutdown() { impl_->shutdown(); }

std::shared_ptr<Frame> GPUMemoryPool::allocate_frame(int w, int h, PixelFormat fmt) {
    auto frame = Frame::create_cpu(w, h, fmt);

    if (impl_->config_.api == GPUApi::CUDA) {
#ifdef ENABLE_CUDA
        frame->buffer().memory = MemoryType::CUDA;
        // In production, allocate CUDA array here
#endif
    }

    return frame;
}

void GPUMemoryPool::recycle(std::shared_ptr<Frame> frame) {
    impl_->pool_.push_back(frame);
}
