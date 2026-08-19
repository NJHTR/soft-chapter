# Runtime Performance Statistics

Date: 07/28/2026 20:04:56
System: Windows Microsoft Windows 11 家庭版 中文版
Kernel: 10.0.26200

## CPU
Model: Intel(R) Core(TM) i9-14900HX
Cores: 24 physical, 32 logical
Max Clock: 2200 MHz

## Memory
Total: 15.7 GB
Free: 1.8 GB (11.2%)

## GPU
name, temperature.gpu, utilization.gpu [%], utilization.memory [%], power.draw [W], clocks.current.graphics [MHz], clocks.current.memory [MHz]
NVIDIA GeForce RTX 4060 Laptop GPU, 49, 0 %, 0 %, 1.81 W, 210 MHz, 405 MHz

## VRAM
memory.total [MiB], memory.used [MiB], memory.free [MiB]
8188 MiB, 71 MiB, 7887 MiB

## Video Encoding Performance Summary

| Encoder | FPS | Speed vs Realtime | Bitrate | Status |
|---------|-----|-------------------|---------|--------|
| x264 (software) | 258 fps | 8.5x | 4 Mbps | ✅ Working (project X264Encoder) |
| x265 (software) | 58 fps | 1.9x | 4 Mbps | ⚠️ ffmpeg works, project X265Encoder.cpp missing |
| NVENC H.264 | N/A | N/A | N/A | ❌ Driver 556.29 < SDK req 570 (ffmpeg 8.1.1 compiled with NVENC SDK v13) |
| NVENC H.265 | N/A | N/A | N/A | ❌ Same driver issue |
| NVENC AV1 | N/A | N/A | N/A | ❌ Same driver issue |

## Camera Capture Performance

| Metric | Value |
|--------|-------|
| Camera Device | ACER HD User Facing (1280x720) |
| Capture FPS | 30 fps (native) |
| Recorded Duration | 10.03s |
| Total Frames | 301 |
| Average Bitrate | 4.66 Mbps (captured H.264) |
| File Size | 5.7 MB (10s, 1280x720@30fps) |

## AI Processing Performance (simulated via ffmpeg)

| Stage | Processing Time (301 frames) | FPS Equivalent | Notes |
|-------|---------------------------|---------------|-------|
| Beauty (skin smooth) | 1.19s | 253 fps | hqdn3d filter |
| Denoise (nlmeans) | 13.99s | 22 fps | Non-local means (computationally heavy) |
| Super Resolution | 0.91s | 330 fps | Lanczos scale up/down |
| HDR Tone Mapping | 0.09s | ~3000+ fps | Hable tonemap |
| Sharpen | 0.57s | 528 fps | Unsharp mask |

## Project Test Results

| Test | Result |
|-----|--------|
| Frame creation test | ✅ PASS |
| GPU memory pool test | ✅ PASS (SKIP - no CUDA) |
| Capture initialization test | ✅ PASS |
| Pipeline configuration test | ✅ PASS |
| Encoder creation test | ✅ PASS |
| Bitrate controller (good network) | ✅ PASS |
| Bitrate controller (bad network) | ✅ PASS |
| Network configuration test | ✅ PASS |

--- End Performance Report ---
