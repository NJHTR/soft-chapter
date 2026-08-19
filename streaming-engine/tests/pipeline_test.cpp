#include "common/frame.h"
#include "common/pipeline.h"
#include "capture/capture.h"
#include "gpu/gpu_pipeline.h"
#include "encoder/encoder.h"
#include "network/network.h"
#include <iostream>
#include <cassert>

// Test: Frame creation and memory management
void test_frame_creation() {
    auto frame = Frame::create_cpu(1920, 1080, PixelFormat::RGBA);
    assert(frame != nullptr);
    assert(frame->buffer().width == 1920);
    assert(frame->buffer().height == 1080);
    assert(frame->buffer().format == PixelFormat::RGBA);
    assert(frame->buffer().data != nullptr);
    std::cout << "[PASS] Frame creation test\n";
}

// Test: GPU memory pool
void test_gpu_memory_pool() {
    GPUConfig config;
    config.api = GPUApi::CUDA;
    config.device_id = 0;

    GPUMemoryPool pool;
    bool ok = pool.init(config);
    if (!ok) {
        std::cout << "[SKIP] GPU memory pool test (no CUDA)\n";
        return;
    }

    auto frame = pool.allocate_frame(1920, 1080, PixelFormat::NV12);
    assert(frame != nullptr);
    assert(frame->buffer().memory == MemoryType::CUDA);

    pool.recycle(frame);
    std::cout << "[PASS] GPU memory pool test\n";
    pool.shutdown();
}

// Test: Capture initialization
void test_capture_init() {
    CaptureConfig config;
    config.source = CaptureSource::FFmpeg;
    config.width = 3840;
    config.height = 2160;
    config.fps = 60;
    config.ffmpeg_input_url = "test://input";

    auto capture = create_capture(config);
    assert(capture != nullptr);
    assert(capture->source() == CaptureSource::FFmpeg);
    std::cout << "[PASS] Capture initialization test\n";
}

// Test: Pipeline configuration
void test_pipeline_config() {
    PipelineConfig config;
    config.input_width = 3840;
    config.input_height = 2160;
    config.output_width = 1920;
    config.output_height = 1080;
    config.fps = 60;
    config.enable_denoise = true;
    config.enable_beauty = true;
    config.enable_super_res = true;
    config.encoder = "nvenc";
    config.encoder_codec = "av1";
    config.bitrate = 4000000;

    VideoPipeline pipeline;
    bool ok = pipeline.init(config);
    if (!ok) {
        std::cout << "[SKIP] Pipeline init test (no GPU)\n";
        return;
    }

    assert(pipeline.config().input_width == 3840);
    assert(pipeline.config().encoder_codec == "av1");
    std::cout << "[PASS] Pipeline configuration test\n";
    pipeline.shutdown();
}

// Test: Encoder creation
void test_encoder_creation() {
    EncoderConfig config;
    config.type = EncoderType::NVENC;
    config.codec = Codec::AV1;
    config.width = 1920;
    config.height = 1080;
    config.fps = 60;
    config.bitrate = 4000000;

    auto encoder = create_encoder(config);
    assert(encoder != nullptr);
    assert(encoder->type() == EncoderType::NVENC);
    assert(encoder->codec() == Codec::AV1);
    std::cout << "[PASS] Encoder creation test\n";
}

// Test: Bitrate controller
void test_bitrate_controller() {
    BitrateConfig config;
    config.initial_bitrate = 4000000;
    config.max_bitrate = 8000000;
    config.min_bitrate = 500000;

    BitrateController ctrl;
    ctrl.init(config);
    assert(ctrl.current_bitrate() == 4000000);

    // Simulate good network
    NetworkMetrics good;
    good.rtt_ms = 20.0;
    good.packet_loss_ratio = 0.0;
    good.throughput_bps = 10000000;

    int bitrate = ctrl.update(good, 10000);
    assert(bitrate > 4000000);
    std::cout << "[PASS] Bitrate controller (good network) test\n";

    // Simulate bad network
    NetworkMetrics bad;
    bad.rtt_ms = 500.0;
    bad.packet_loss_ratio = 0.1;
    bad.throughput_bps = 1000000;

    bitrate = ctrl.update(bad, 50000);
    assert(bitrate < 4000000);
    std::cout << "[PASS] Bitrate controller (bad network) test\n";
}

// Test: Network streamer configuration
void test_network_config() {
    StreamConfig config;
    config.protocol = StreamProtocol::SRT;
    config.server_url = "srt://localhost:9000";
    config.stream_key = "test_stream";
    config.srt_latency_ms = 120;

    assert(config.protocol == StreamProtocol::SRT);
    assert(config.srt_latency_ms == 120);
    std::cout << "[PASS] Network configuration test\n";
}

int main() {
    std::cout << "=== Streaming Engine Tests ===\n\n";

    test_frame_creation();
    test_gpu_memory_pool();
    test_capture_init();
    test_pipeline_config();
    test_encoder_creation();
    test_bitrate_controller();
    test_network_config();

    std::cout << "\n=== All tests completed ===\n";
    return 0;
}
