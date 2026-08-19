# STEP 2 — CMake Build Report

## Build Configuration

- Generator: **Visual Studio 18 2026** (MSVC 14.50)
- Platform: **x64**
- C++ Standard: **C++20**
- Build Type: **Release** (verified)
- CMake: **4.4.0**

## Targets

| Target | Type | Built | Size |
|--------|------|-------|------|
| `streaming_engine_common` | Static Lib (.lib) | ✅ | 164 KB |
| `streaming_engine_capture` | Static Lib (.lib) | ✅ | 423 KB |
| `streaming_engine_gpu` | Static Lib (.lib) | ✅ | 534 KB |
| `streaming_engine_ai` | Static Lib (.lib) | ✅ | 577 KB |
| `streaming_engine_beauty` | Static Lib (.lib) | ✅ | 189 KB |
| `streaming_engine_encoder` | Static Lib (.lib) | ✅ | 524 KB |
| `streaming_engine_network` | Static Lib (.lib) | ✅ | 1.04 MB |
| `streaming_engine_server` | Static Lib (.lib) | ✅ | 348 KB |
| `streaming_engine_player` | INTERFACE | ✅ (header only) | N/A |
| `streaming_engine_denoise` | INTERFACE | ✅ (header only) | N/A |
| `streaming_engine_super_res` | INTERFACE | ✅ (header only) | N/A |
| `streaming_engine_tests` | Executable (.exe) | ✅ | 53 KB |
| `ALL_BUILD` | Utility | ✅ | N/A |
| `ZERO_CHECK` | Utility | ✅ | N/A |
| **Total .lib size** | | | **~3.6 MB** |

## Third-Party Dependencies

| Library | Version | Source | Status |
|---------|---------|--------|--------|
| **FFmpeg** | 8.1.1 (avcodec 62.28.101) | vcpkg `x64-windows` | ✅ **Found & linked** |
| **OpenCV** | 4.12.0 | vcpkg `x64-windows` | ✅ **Found & linked** (core, imgproc) |
| **OpenGL** | System | Windows SDK | ✅ **Found & linked** |
| **Threads** | — | MSVC | ✅ **Found** |
| **Protobuf** | 33.4.0 | vcpkg (OpenCV dep) | ✅ **Found** |
| **TIFF** | 4.7.1 | vcpkg (OpenCV dep) | ✅ **Found** |
| **libpng** | 1.6.58 | vcpkg (OpenCV dep) | ✅ transitive |
| **libjpeg-turbo** | 3.1.4.1 | vcpkg (OpenCV dep) | ✅ transitive |
| **zlib** | 1.3.2 | vcpkg (OpenCV dep) | ✅ transitive |

## Feature Flags (Build-Time)

| Flag | Default | Build Value | Actually Used? |
|------|---------|-------------|----------------|
| `ENABLE_CUDA` | ON | **OFF** (CUDA not found) | ❌ CUDA code not compiled |
| `ENABLE_VULKAN` | ON | **OFF** (Vulkan not found) | ❌ Vulkan code not compiled |
| `ENABLE_TENSORRT` | ON | **OFF** (CUDA not found) | ❌ TensorRT code not compiled |
| `ENABLE_NVENC` | ON | **OFF** (nvEncodeAPI.h not found) | ❌ NVENC code not compiled |
| `ENABLE_SRT` | ON | **OFF** (SRT not found) | ❌ SRT code not compiled |
| `ENABLE_WEBRTC` | ON | **OFF** (disabled) | ❌ WebRTC code not compiled |
| `ENABLE_ONNX` | ON | **OFF** (disabled) | ❌ ONNX code not compiled |
| `ENABLE_QSV` | OFF | OFF | ❌ QuickSync not compiled |
| `ENABLE_METAL` | OFF | OFF | ❌ Metal not compiled |
| `ENABLE_NCNN` | OFF | OFF | ❌ NCNN not compiled |
| `ENABLE_SERVER` | ON | ON | ✅ compiled |
| `ENABLE_TESTS` | ON | ON | ✅ compiled |
| `ENABLE_CURL` | — | OFF (CURL not found) | ❌ CURL code not compiled |

## Key Finding: Feature Flags

**Out of 12 optional feature flags, 10 evaluate to OFF in the current build.**

The only features enabled are `ENABLE_SERVER` and `ENABLE_TESTS`. All GPU-accelerated and hardware encoding paths are excluded.

The build produces only software fallback paths:
- x264 via FFmpeg libavcodec (instead of NVENC)
- CPU-based capture (instead of CUDA)
- CPU-based processing (instead of GPU pipeline)
- RTMP fallback (instead of SRT/WebRTC)

## What Would Be Needed for a Full Build

| Feature | Requires |
|---------|----------|
| CUDA | NVIDIA CUDA Toolkit ≥ 12.x |
| NVENC | NVIDIA Video Codec SDK (nvEncodeAPI.h) |
| TensorRT | NVIDIA TensorRT ≥ 8.x |
| Vulkan | Vulkan SDK ≥ 1.3 |
| SRT | haivision/srt library |
| WebRTC | libwebrtc or Google's build |
| ONNX Runtime | onnxruntime ≥ 1.15 |
| CURL | libcurl (simple vcpkg install) |
| OpenCV full | Already installed via vcpkg |
| FFmpeg full | Already installed via vcpkg |
