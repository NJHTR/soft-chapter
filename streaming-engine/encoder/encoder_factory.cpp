#include "encoder/encoder.h"
#include "encoder/nvenc/nvenc_encoder.h"
#include "encoder/x264/x264_encoder.h"
#include <iostream>

std::unique_ptr<Encoder> create_encoder(const EncoderConfig& config) {
    switch (config.type) {
        case EncoderType::NVENC:
#ifdef ENABLE_NVENC
            return std::make_unique<NvEncEncoder>();
#else
            std::cerr << "NVENC not available, falling back to x264" << std::endl;
            return std::make_unique<X264Encoder>();
#endif
        case EncoderType::x264:
            return std::make_unique<X264Encoder>();
        case EncoderType::x265:
            return std::make_unique<X265Encoder>();
        default:
            std::cerr << "Unknown encoder type, using x264" << std::endl;
            return std::make_unique<X264Encoder>();
    }
}
