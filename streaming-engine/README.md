# Streaming Engine

High-performance real-time video streaming engine for the douyin project. Targets TikTok Live quality.

## Architecture

```
Camera (4K) → GPU Oversample → AI Denoise → AI Beauty → 
AI Super Res → Color Grading → HDR → Sharpen → 
Encoder (NVENC AV1) → Stream (SRT/WebRTC) → CDN → Player
```

All processing on GPU. Zero CPU copy.

## Build

### Prerequisites
- CMake 3.25+
- CUDA 12.0+ (for NVENC/TensorRT)
- FFmpeg (libavcodec, libavformat)
- OpenCV 4.x
- Vulkan SDK (optional)
- TensorRT 10 (optional)

### Windows
```bash
cd streaming-engine
cmake -B build -G "Visual Studio 17 2022"
cmake --build build --config Release
```

### Linux
```bash
cmake -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j$(nproc)
```

## Modules

| Path | Description |
|------|-------------|
| `capture/` | Camera capture (MF, DShow, AVFoundation, V4L2, Camera2) |
| `gpu/` | GPU pipeline (CUDA, OpenGL, Vulkan, Metal) |
| `ai/` | AI inference (TensorRT, ONNX, NCNN) |
| `beauty/` | TikTok beauty pipeline |
| `denoise/` | AI denoise (FastDVDNet, NAFNet) |
| `super_resolution/` | AI upscaling (RealESRGAN, SwinIR) |
| `encoder/` | Video encoders (NVENC, x264, x265, AV1) |
| `network/` | Streaming protocols (SRT, WebRTC, RTMP) |
| `server/` | Streaming server integration (SRS, MediaMTX) |
| `player/` | Playback (WebCodecs, MSE, WebRTC) |

## Integration

### Java (Spring Boot)
The engine exposes JNI methods for Java integration.
See `com.douyin.engine.StreamingEngine` in the Spring Boot server.

### JavaScript (Browser)
The `src/utils/streaming/` directory contains WebCodecs-based
sender and player implementations for browser-side streaming.

## Quality Targets

| Metric | Target |
|--------|--------|
| Resolution | 1080P (from 4K oversample) |
| Frame rate | 60 FPS |
| Bitrate | 500kbps - 8Mbps adaptive |
| Latency | < 500ms end-to-end |
| Codec | AV1 primary, H.265 fallback |
| GPU memory | < 2GB |
