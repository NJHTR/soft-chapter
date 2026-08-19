# Streaming Engine Architecture

## Overview

High-performance real-time video streaming engine targeting TikTok Live quality.

## Pipeline

```
Camera (4K)
  │
  ▼ GPU Memory (CUDA/OpenGL/Vulkan)
  ├── Oversample 4K→1080P (Super Sampling)
  ├── AI Denoise (FastDVDNet/NAFNet)
  ├── AI Beauty (MediaPipe FaceMesh + GPU Shader)
  ├── AI Super Resolution (RealESRGAN - 720P→1080P)
  ├── Color Correction (3D LUT / ACES)
  ├── HDR Tone Mapping (BT.709 / BT.2020 / HLG)
  ├── AI Sharpen (Guided Filter / Edge-Aware)
  │
  ▼ Encoder (NVENC/AV1/H.265)
  ├── Dynamic Bitrate (500kbps~8Mbps)
  │
  ▼ Streamer (SRT/WebRTC/WHIP)
  │
  ▼ Streaming Server (SRS/MediaMTX)
  ├── Ingest
  ├── Transcode (ABR: 480P/720P/1080P)
  ├── Edge Cache
  │
  ▼ CDN (Multi-region)
  │
  ▼ Player (WebCodecs/WebRTC/GPU Decode)
```

## Key Design Decisions

1. **4K Oversampling**: Capture at 4K, GPU downsample to 1080P for superior quality
2. **Zero-copy GPU Pipeline**: All processing stays on GPU, no CPU memcpy
3. **AI-first**: Denoise, super-res, beauty all use GPU-accelerated AI models
4. **Tiered Encoding**: Primary: NVENC AV1, Fallback: NVENC H.265, Software: x264
5. **Dynamic Bitrate**: Real-time network monitoring adjusts bitrate per-frame
6. **Low-latency Transport**: SRT for reliable, WebRTC for ultra-low-latency

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Core | C++20, CMake |
| GPU Compute | CUDA 12, OpenGL 4.6, Vulkan 1.3, Metal |
| AI Runtime | TensorRT 10, ONNX Runtime, NCNN |
| Capture | MediaFoundation, AVFoundation, V4L2, Camera2 |
| Encoder | NVENC, Intel QSV, AMF, x264, x265, SVT-AV1 |
| Network | SRT, WebRTC (libdatachannel), QUIC (lsquic) |
| Server | SRS 6.0, MediaMTX, ZLMediaKit |
| Player | WebCodecs, MSE, WebRTC, Canvas 2D |
| Integration | JNI (Java <-> C++ bridge) |

## Performance Targets

| Metric | Target |
|--------|--------|
| End-to-end latency | < 500ms |
| Frame rate | 60 FPS |
| Resolution | 1080P (upscaled from 720P) |
| Bitrate range | 500kbps - 8Mbps |
| GPU memory | < 2GB |
| CPU usage (encoding) | < 30% (with HW encode) |
