#include "common/frame.h"
#include <cstring>
#include <cstdlib>
#ifdef _MSC_VER
#include <malloc.h>
#endif

std::shared_ptr<Frame> Frame::create_cpu(int w, int h, PixelFormat fmt) {
    auto frame = std::make_shared<Frame>();
    auto& buf = frame->buffer();
    buf.width = w;
    buf.height = h;
    buf.format = fmt;
    buf.memory = MemoryType::CPU;
    buf.timestamp = 0;
    buf.frame_index = 0;

    // Calculate size based on format
    switch (fmt) {
        case PixelFormat::NV12:
            buf.size = w * h * 3 / 2;  // Y + UV planes
            buf.stride[0] = w;
            buf.stride[1] = w;
            break;
        case PixelFormat::I420:
            buf.size = w * h * 3 / 2;
            buf.stride[0] = w;
            buf.stride[1] = w / 2;
            buf.stride[2] = w / 2;
            break;
        case PixelFormat::RGBA:
        case PixelFormat::BGRA:
            buf.size = w * h * 4;
            buf.stride[0] = w * 4;
            break;
        case PixelFormat::P010:
            buf.size = w * h * 3;  // 10-bit: 2 bytes per sample
            buf.stride[0] = w * 2;
            buf.stride[1] = w * 2;
            break;
    }

#ifdef _MSC_VER
    buf.data = (uint8_t*)_aligned_malloc(buf.size, 64);
#else
    buf.data = (uint8_t*)aligned_alloc(64, buf.size);
#endif
    memset(buf.data, 0, buf.size);

    frame->set_release_callback([data = buf.data]() {
#ifdef _MSC_VER
        _aligned_free(data);
#else
        free(data);
#endif
    });

    return frame;
}
