#include "encoder/nvenc/nvenc_encoder.h"
#include <iostream>
#include <cstring>
#include <vector>
#include <thread>
#include <atomic>

#ifdef _WIN32
#define NOMINMAX
#include <windows.h>
#endif

// NVIDIA Video Codec SDK header
// https://developer.nvidia.com/nvidia-video-codec-sdk
#ifdef ENABLE_NVENC
#include <nvEncodeAPI.h>
#include <cuda.h>
#include <cuda_runtime.h>

// NVENC AV1 specific (ADA Lovelace+)
#ifndef NV_ENC_AV1
#define NV_ENC_AV1 9
#endif

class NvEncEncoder::Impl {
public:
    EncoderConfig config_;
    EncodedCallback callback_;
    std::atomic<bool> initialized_{false};

    // CUDA
    CUcontext cuda_ctx_ = nullptr;
    CUdevice cuda_device_ = 0;

    // NVENC
    void* nvenc_handle_ = nullptr;
    NV_ENCODE_API_FUNCTION_LIST nvenc_;
    NV_ENC_INITIALIZE_PARAMS init_params_{};
    NV_ENC_CONFIG encode_config_{};
    NV_ENC_SESSION* session_ = nullptr;

    // Input resources (registered CUDA textures)
    NV_ENC_REGISTERED_RESOURCE* registered_resources_[3] = {}; // triple buffer
    int current_buffer_ = 0;

    // Output bitstream
    NV_ENC_OUTPUT_PTR output_bitstream_ = nullptr;
    std::vector<uint8_t> bitstream_buffer_;

    // Statistics
    std::atomic<int64_t> total_frames_{0};
    std::atomic<int64_t> total_bytes_{0};

    ~Impl() { shutdown(); }

    bool init(const EncoderConfig& config) {
        config_ = config;

#ifdef ENABLE_NVENC
        if (!init_cuda()) return false;
        if (!init_nvenc()) return false;

        std::cout << "[NVENC] Initialized: "
                  << config_.width << "x" << config_.height
                  << " @" << config_.fps << "fps"
                  << " " << config_.bitrate / 1000 << "kbps";
        switch (config_.codec) {
            case Codec::AV1:  std::cout << " AV1"; break;
            case Codec::H265: std::cout << " H.265"; break;
            case Codec::H264: std::cout << " H.264"; break;
        }
        std::cout << " preset=P" << config_.nvenc_preset << std::endl;

        initialized_ = true;
        return true;
#else
        std::cerr << "[NVENC] Not enabled in build (ENABLE_NVENC=OFF)" << std::endl;
        return false;
#endif
    }

#ifdef ENABLE_NVENC
    bool init_cuda() {
        cuInit(0);
        int device_count = 0;
        cuDeviceGetCount(&device_count);
        if (device_count == 0) {
            std::cerr << "[NVENC] No CUDA device found" << std::endl;
            return false;
        }

        int device_id = config_.nvenc_device_override >= 0
            ? config_.nvenc_device_override : config_.device_id;

        cuDeviceGet(&cuda_device_, device_id);
        cuCtxCreate(&cuda_ctx_, 0, cuda_device_);

        char name[128];
        cuDeviceGetName(name, sizeof(name), cuda_device_);
        std::cout << "[NVENC] CUDA device: " << name << " (ID: " << device_id << ")" << std::endl;
        return true;
    }

    bool init_nvenc() {
        // Load NVENC API
        NV_ENCODE_API_FUNCTION_LIST nvenc = {};
        nvenc.version = NV_ENCODE_API_FUNCTION_LIST_VER;

        // Open NVENC shared library
#ifdef _WIN32
        nvenc_handle_ = (void*)LoadLibraryA("nvEncodeAPI64.dll");
#else
        nvenc_handle_ = dlopen("libnvcuvid.so", RTLD_LAZY);
#endif
        if (!nvenc_handle_) {
            std::cerr << "[NVENC] Failed to load NVENC library" << std::endl;
            return false;
        }

        // Get NVENC API function
        using NvEncodeAPICreateInstanceFunc = NVENCSTATUS (*)(NV_ENCODE_API_FUNCTION_LIST*);

#ifdef _WIN32
        auto create_instance = (NvEncodeAPICreateInstanceFunc)
            GetProcAddress((HMODULE)nvenc_handle_, "NvEncodeAPICreateInstance");
#else
        auto create_instance = (NvEncodeAPICreateInstanceFunc)
            dlsym(nvenc_handle_, "NvEncodeAPICreateInstance");
#endif
        if (!create_instance) {
            std::cerr << "[NVENC] Failed to get NvEncodeAPICreateInstance" << std::endl;
            return false;
        }

        // Open encode session
        NVENCSTATUS status = create_instance(&nvenc);
        if (status != NV_ENC_SUCCESS) {
            std::cerr << "[NVENC] Failed to create instance: " << status << std::endl;
            return false;
        }
        nvenc_ = nvenc;

        // Initialize encoder
        memset(&init_params_, 0, sizeof(init_params_));
        init_params_.version = NV_ENC_INITIALIZE_PARAMS_VER;
        init_params_.encodeWidth = config_.width;
        init_params_.encodeHeight = config_.height;
        init_params_.darWidth = config_.width;
        init_params_.darHeight = config_.height;
        init_params_.frameRateNum = config_.fps;
        init_params_.frameRateDen = 1;
        init_params_.enableEncodeAsync = 1;
        init_params_.enablePTD = 1;
        init_params_.enableOutputInVideoMemory = 0; // CPU-side output for now

        // Configure encoding params
        memset(&encode_config_, 0, sizeof(encode_config_));
        encode_config_.version = NV_ENC_CONFIG_VER;
        encode_config_.profileGUID = get_codec_guid();
        encode_config_.gopLength = config_.gop_size;
        encode_config_.frameIntervalP = config_.b_frames + 1;
        encode_config_.frameFieldMode = NV_ENC_PARAMS_FRAME_FIELD_MODE_FRAME;

        // Rate control
        encode_config_.rcParams.version = NV_ENC_RC_PARAMS_VER;
        encode_config_.rcParams.rateControlMode = NV_ENC_PARAMS_RC_VBR;
        encode_config_.rcParams.averageBitRate = config_.bitrate;
        encode_config_.rcParams.maxBitRate = config_.max_bitrate;
        encode_config_.rcParams.vbvBufferSize = config_.bitrate / config_.fps;
        encode_config_.rcParams.vbvInitialDelay = 0;

        // Preset
        switch (config_.nvenc_preset) {
            case 1: init_params_.encodePreset = NV_ENC_PRESET_P1_GUID; break;
            case 2: init_params_.encodePreset = NV_ENC_PRESET_P2_GUID; break;
            case 3: init_params_.encodePreset = NV_ENC_PRESET_P3_GUID; break;
            case 4: init_params_.encodePreset = NV_ENC_PRESET_P4_GUID; break;
            case 5: init_params_.encodePreset = NV_ENC_PRESET_P5_GUID; break;
            case 6: init_params_.encodePreset = NV_ENC_PRESET_P6_GUID; break;
            case 7: init_params_.encodePreset = NV_ENC_PRESET_P7_GUID; break;
            default: init_params_.encodePreset = NV_ENC_PRESET_P6_GUID; break;
        }

        init_params_.encodeConfig = &encode_config_;

        // Set codec-specific params
        if (config_.codec == Codec::H264) {
            encode_config_.encodeCodecConfig.h264Config.level = NV_ENC_LEVEL_AUTOSELECT;
            encode_config_.encodeCodecConfig.h264Config.chromaFormatIDC = 1; // YUV420
        } else if (config_.codec == Codec::H265) {
            encode_config_.encodeCodecConfig.hevcConfig.level = NV_ENC_LEVEL_AUTOSELECT;
            encode_config_.encodeCodecConfig.hevcConfig.chromaFormatIDC = 1;
        } else if (config_.codec == Codec::AV1) {
            encode_config_.encodeCodecConfig.av1Config.level = NV_ENC_LEVEL_AV1_AUTOSELECT;
            encode_config_.encodeCodecConfig.av1Config.chromaFormatIDC = 1;
        }

        status = nvenc.nvEncInitializeEncoder(session_, &init_params_);
        if (status != NV_ENC_SUCCESS) {
            std::cerr << "[NVENC] Init failed: " << status << std::endl;
            return false;
        }

        // Allocate output bitstream buffer
        NV_ENC_CREATE_BITSTREAM_BUFFER bitstream = {};
        bitstream.version = NV_ENC_CREATE_BITSTREAM_BUFFER_VER;
        status = nvenc.nvEncCreateBitstreamBuffer(session_, &bitstream);
        if (status != NV_ENC_SUCCESS) {
            std::cerr << "[NVENC] Failed to create bitstream buffer" << std::endl;
            return false;
        }
        output_bitstream_ = bitstream.bitstreamBuffer;
        bitstream_buffer_.resize(4 * 1024 * 1024); // 4MB output buffer

        return true;
    }

    GUID get_codec_guid() {
        switch (config_.codec) {
            case Codec::H264: return NV_ENC_CODEC_H264_GUID;
            case Codec::H265: return NV_ENC_CODEC_HEVC_GUID;
            case Codec::AV1:  return NV_ENC_CODEC_AV1_GUID;
            default: return NV_ENC_CODEC_H264_GUID;
        }
    }

    bool encode_frame(std::shared_ptr<Frame> frame,
                      std::vector<uint8_t>& packet_data) {
        if (!initialized_) return false;
        if (!frame) return false;

        auto& buf = frame->buffer();

        // Map input frame as NVENC input resource
        NV_ENC_MAP_INPUT_RESOURCE map_res = {};
        map_res.version = NV_ENC_MAP_INPUT_RESOURCE_VER;

        // Create registered resource from CUDA array or system memory
        NV_ENC_REGISTER_RESOURCE register_res = {};
        register_res.version = NV_ENC_REGISTER_RESOURCE_VER;

        if (buf.memory == MemoryType::CUDA && buf.gpu.cuda.cuda_resource) {
            // Zero-copy from CUDA texture
            register_res.resourceType = NV_ENC_INPUT_RESOURCE_TYPE_CUDADEVICEPTR;
            register_res.resourceToRegister = buf.gpu.cuda.cuda_resource;
        } else {
            // System memory fallback (NV12 format expected)
            // NVENC expects NV12 input by default
            register_res.resourceType = NV_ENC_INPUT_RESOURCE_TYPE_SYSTEM_MEMORY;
            register_res.resourceToRegister = buf.data;
            register_res.width = buf.width;
            register_res.height = buf.height;
            register_res.pitch = buf.stride[0];
            register_res.bufferFormat = NV_ENC_BUFFER_FORMAT_NV12_PL;
        }

        register_res.bufferUsage = NV_ENC_INPUT_BUFFER_USAGE_ENCODE;
        NVENCSTATUS status = nvenc.nvEncRegisterResource(session_, &register_res);
        if (status != NV_ENC_SUCCESS) return false;

        // Map the registered resource for encoding
        map_res.registeredResource = register_res.registeredResource;
        status = nvenc.nvEncMapInputResource(session_, &map_res);
        if (status != NV_ENC_SUCCESS) return false;

        // Submit encode frame
        NV_ENC_PIC_PARAMS pic_params = {};
        pic_params.version = NV_ENC_PIC_PARAMS_VER;
        pic_params.inputBuffer = map_res.mappedResource;
        pic_params.bufferFmt = register_res.bufferFormat;
        pic_params.inputWidth = buf.width;
        pic_params.inputHeight = buf.height;
        pic_params.inputPitch = buf.stride[0] ? buf.stride[0] : buf.width;
        pic_params.pictureStruc = NV_ENC_PIC_STRUCT_FRAME;
        pic_params.inputTimeStamp = buf.timestamp;
        pic_params.inputDuration = 1000 / config_.fps;
        pic_params.encodePicFlags = 0;

        // Force keyframe on I-frame interval
        if (total_frames_ % config_.gop_size == 0) {
            pic_params.encodePicFlags |= NV_ENC_PIC_FLAG_FORCEIDR;
        }

        // Async encode
        NV_ENC_LOCK_BITSTREAM lock = {};
        lock.version = NV_ENC_LOCK_BITSTREAM_VER;

        status = nvenc.nvEncEncodePicture(session_, &pic_params);
        if (status != NV_ENC_SUCCESS && status != NV_ENC_ERR_NEED_MORE_INPUT) {
            return false;
        }

        // Get encoded bitstream
        if (status == NV_ENC_SUCCESS) {
            status = nvenc.nvEncLockBitstream(session_, output_bitstream_, &lock);
            if (status == NV_ENC_SUCCESS) {
                bool is_keyframe = (lock.pictureType & NV_ENC_PIC_TYPE_IDR) != 0;
                packet_data.resize(lock.bitstreamSizeInBytes);
                memcpy(packet_data.data(), lock.bitstreamBufferPtr, lock.bitstreamSizeInBytes);
                nvenc.nvEncUnlockBitstream(session_, output_bitstream_);

                total_frames_++;
                total_bytes_ += lock.bitstreamSizeInBytes;

                if (callback_) {
                    callback_(packet_data, buf.timestamp * 1000, is_keyframe);
                }
            }
        }

        // Unmap and unregister resources
        nvenc.nvEncUnmapInputResource(session_, map_res.mappedResource);
        nvenc.nvEncUnregisterResource(session_, register_res.registeredResource);

        return !packet_data.empty();
    }

    void set_bitrate(int bitrate) {
        if (!initialized_) return;
        config_.bitrate = bitrate;
        config_.max_bitrate = std::max(bitrate, config_.min_bitrate);

        // NVENC dynamic bitrate update
        NV_ENC_RECONFIGURE_PARAMS reconf = {};
        reconf.version = NV_ENC_RECONFIGURE_PARAMS_VER;
        reconf.forceReconfig = 1;
        reconf.resetEncoder = 0;

        reconf.rcParams.averageBitRate = bitrate;
        reconf.rcParams.maxBitRate = config_.max_bitrate;
        reconf.rcParams.vbvBufferSize = bitrate / config_.fps;

        nvenc.nvEncReconfigureEncoder(session_, &reconf);
    }

    void request_keyframe() {
        // Force IDR on next frame via encode flag
    }

    void shutdown() {
        if (!initialized_) return;
        initialized_ = false;

        if (output_bitstream_ && session_) {
            nvenc.nvEncDestroyBitstreamBuffer(session_, output_bitstream_);
        }
        if (session_) {
            nvenc.nvEncDestroyEncoder(session_);
        }
        if (cuda_ctx_) {
            cuCtxDestroy(cuda_ctx_);
        }
        if (nvenc_handle_) {
            dlclose(nvenc_handle_);
        }
    }
};

#else
// Stub: NVENC not available
class NvEncEncoder::Impl {
public:
    EncoderConfig config_;
    EncodedCallback callback_;
    bool initialized_ = false;
    int64_t pts_ = 0;

    bool init(const EncoderConfig& config) {
        config_ = config;
        std::cerr << "[NVENC] Not available. Use x264/x265 encoder." << std::endl;
        return false;
    }

    bool encode_frame(std::shared_ptr<Frame> frame,
                      std::vector<uint8_t>& packet) {
        return false;
    }

    void set_bitrate(int bitrate) {}
    void request_keyframe() {}
    void shutdown() {}
};
#endif

NvEncEncoder::NvEncEncoder() : impl_(std::make_unique<Impl>()) {}
NvEncEncoder::~NvEncEncoder() = default;

bool NvEncEncoder::init(const EncoderConfig& config) { return impl_->init(config); }
bool NvEncEncoder::encode(std::shared_ptr<Frame> frame, std::vector<uint8_t>& packet) {
    return impl_->encode_frame(frame, packet);
}
bool NvEncEncoder::flush(std::vector<uint8_t>& packet) { return false; }
void NvEncEncoder::shutdown() { impl_->shutdown(); }
void NvEncEncoder::set_bitrate(int bitrate) { impl_->set_bitrate(bitrate); }
void NvEncEncoder::request_keyframe() { impl_->request_keyframe(); }
void NvEncEncoder::set_output_callback(EncodedCallback cb) { impl_->callback_ = std::move(cb); }

Codec NvEncEncoder::codec() const { return impl_->config_.codec; }
std::string NvEncEncoder::codec_name() const {
    switch (impl_->config_.codec) {
        case Codec::AV1: return "av1_nvenc";
        case Codec::H265: return "hevc_nvenc";
        case Codec::H264: return "h264_nvenc";
    }
    return "av1_nvenc";
}
