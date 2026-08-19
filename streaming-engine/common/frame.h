#pragma once

#include <cstdint>
#include <memory>
#include <vector>
#include <functional>

enum class PixelFormat {
    NV12,
    I420,
    RGBA,
    BGRA,
    P010,  // 10-bit HDR
};

enum class MemoryType {
    CPU,
    CUDA,
    OpenGL,
    Vulkan,
    Metal,
};

struct FrameBuffer {
    uint8_t* data = nullptr;
    size_t size = 0;
    int width = 0;
    int height = 0;
    PixelFormat format = PixelFormat::NV12;
    MemoryType memory = MemoryType::CPU;
    int stride[4] = {0};
    int64_t timestamp = 0;
    int frame_index = 0;

    // GPU-specific handles
    union {
        struct { void* cuda_resource; } cuda;
        struct { uint32_t texture_id; } gl;
        struct { uint64_t vk_image; } vk;
        struct { void* mtl_texture; } mtl;
    } gpu;
};

class Frame {
public:
    using ReleaseCallback = std::function<void()>;

    Frame() = default;
    ~Frame() { if (release_) release_(); }

    Frame(const Frame&) = delete;
    Frame& operator=(const Frame&) = delete;
    Frame(Frame&&) = default;
    Frame& operator=(Frame&&) = default;

    FrameBuffer& buffer() { return buf_; }
    const FrameBuffer& buffer() const { return buf_; }

    void set_release_callback(ReleaseCallback cb) { release_ = std::move(cb); }

    static std::shared_ptr<Frame> create_cpu(int w, int h, PixelFormat fmt);

private:
    FrameBuffer buf_;
    ReleaseCallback release_;
};
