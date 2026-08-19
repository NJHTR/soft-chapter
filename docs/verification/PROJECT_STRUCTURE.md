# STEP 1 — Project Structure Report

## ① All Modules

| # | Module | Path | Type | Language |
|---|--------|------|------|----------|
| 1 | **common** | `streaming-engine/common/` | Static library (.lib) | C++20 |
| 2 | **capture** | `streaming-engine/capture/` | Static library (.lib) | C++20 |
| 3 | **gpu** | `streaming-engine/gpu/` | Static library (.lib) | C++20 + GLSL |
| 4 | **ai** | `streaming-engine/ai/` | Static library (.lib) | C++20 |
| 5 | **beauty** | `streaming-engine/beauty/` | Static library (.lib) | C++20 |
| 6 | **denoise** | `streaming-engine/denoise/` | INTERFACE (header-only) | C++20 |
| 7 | **super_resolution** | `streaming-engine/super_resolution/` | INTERFACE (header-only) | C++20 |
| 8 | **encoder** | `streaming-engine/encoder/` | Static library (.lib) | C++20 |
| 9 | **network** | `streaming-engine/network/` | Static library (.lib) | C++20 |
| 10 | **server** | `streaming-engine/server/` | Static library (.lib) | C++20 |
| 11 | **player** | `streaming-engine/player/` | INTERFACE (header-only) | C++20 |
| 12 | **tests** | `streaming-engine/tests/` | Executable (.exe) | C++20 |
| 13 | **frontend** | `src/utils/streaming/` | TypeScript | TypeScript |
| 14 | **frontend** | `src/pages/live/` | Vue 3 | TypeScript/Vue |
| 15 | **backend** | `server/.../engine/` | Java Spring Boot | Java 17+ |
| 16 | **backend** | `server/.../controller/` | Java Spring Boot | Java 17+ |
| 17 | **backend** | `server/.../service/` | Java Spring Boot | Java 17+ |

## ② Module Dependencies

```
streaming_engine_tests
  └─ streaming_engine_common

streaming_engine_ai
  └─ streaming_engine_common

streaming_engine_beauty
  └─ streaming_engine_ai
  └─ streaming_engine_common

streaming_engine_capture
  └─ streaming_engine_common
  └─ opencv_imgproc
  └─ FFMPEG (avcodec/avformat/avutil/swscale/avdevice/avfilter)

streaming_engine_gpu
  └─ streaming_engine_common
  └─ OpenGL
  └─ [optional] CUDA
  └─ [optional] Vulkan

streaming_engine_denoise (INTERFACE)
  └─ streaming_engine_ai

streaming_engine_super_res (INTERFACE)
  └─ streaming_engine_ai

streaming_engine_encoder
  └─ streaming_engine_common
  └─ FFMPEG (avcodec)
  └─ [optional] NVENC (nvEncodeAPI.h)

streaming_engine_network
  └─ streaming_engine_common
  └─ streaming_engine_encoder
  └─ [optional] SRT (srt::srt)
  └─ [optional] WebRTC

streaming_engine_server
  └─ streaming_engine_common
  └─ streaming_engine_encoder
  └─ streaming_engine_network
  └─ [optional] CURL

streaming_engine_player (INTERFACE)
  └─ streaming_engine_common
```

**Java Backend:**
```
LiveController / StreamController
  └─ StreamingSessionManager
       └─ StreamingEngine (JNI → native)
            └─ streaming_engine.dll (not yet built)
```

**Frontend:**
```
LiveCreate.vue
  └─ WebCodecsSender (webcodecs_sender.ts)
       └─ WebCodecs VideoEncoder API
       └─ WebSocket / RTCPeerConnection

LiveWatch.vue
  └─ WebCodecsPlayer (webcodecs_player.ts)
       └─ WebCodecs VideoDecoder API
       └─ WebSocket / RTCPeerConnection

live.ts
  └─ HTTP API calls to Spring Boot
```

## ③ Circular Dependencies

**None found.** The dependency graph is acyclic. All arrows flow one direction:
`common → {capture, gpu, ai, encoder} → {beauty, network} → server → tests`

## ④ Module Interfaces

### common
- `Frame` — video frame buffer (RGB/NV12/P010)
- `VideoPipeline` — pipeline node base class
- `PipelineConfig` — full pipeline configuration
- `StreamingEngine` — top-level orchestrator (C++ class)
- `jni_bridge.h` — JNI native function declarations (7 functions)

### capture
- `Capture` — abstract video capture interface
- `CameraCapture` — camera implementation (platform backends)
- `FFmpegCapture` — FFmpeg avdevice capture
- `create_capture()` — factory function

### gpu
- `GPUPipelineNode` — abstract GPU processing node
- `GPUOversampler` — 4K→1080P Lanczos3 downscale
- `GPUColorConverter` — NV12↔RGBA conversion
- `GPUMemoryPool` — GPU memory allocator
- `ShaderManager` — GLSL shader compile/link/dispatch
- `CUDAPipeline` — CUDA-specific pipeline
- `VulkanPipeline` — Vulkan-specific pipeline

### ai
- `AIModel` — abstract AI model interface
- `TensorRTEngine` — NVIDIA TensorRT runtime
- `AIModelRegistry` — model factory registry
- `create_ai_model()` — factory function

### beauty
- `BeautyPipeline` — skin/eye/lip/face enhancement pipeline
- `BeautyConfig` — beauty filter parameters
- `FaceInfo` — MediaPipe face mesh data

### encoder
- `Encoder` — abstract encoder interface
- `NvEncEncoder` — NVIDIA NVENC (H.264/H.265/AV1)
- `X264Encoder` — software x264 (libavcodec)
- `X265Encoder` — software x265 (libavcodec)
- `BitrateController` — adaptive bitrate logic
- `AudioEncoder` — AAC/Opus/MP3 encoder
- `AudioCapture` — WASAPI/sine wave capture
- `AudioMixer` — multi-source audio mixer
- `create_encoder()` — factory function

### network
- `NetworkStreamer` — abstract streamer interface
- `SRTStreamer` — SRT protocol streamer
- `WebRTCStreamer` — WebRTC/WHIP streamer
- `RTMPStreamer` — RTMP protocol streamer
- `Muxer` — FLV (pure) / MP4 / TS muxer
- `FECEncoder` — forward error correction
- `FECDecoder` — FEC decoding
- `NACKHandler` — retransmission handler
- `NetworkPipe` — streamer + bitrate controller combo
- `create_network_streamer()` — factory function

### server
- `StreamServer` — media server manager (SRS/MediaMTX/ZLMediaKit)
- `Transcoder` — ABR transcoding ladder

### player — INTERFACE only
- No concrete classes defined (see player.h)

### Java backend
- `StreamingEngine.java` — JNI bridge (7 native methods)
- `StreamingSessionManager.java` — session lifecycle
- `StreamController.java` — WebRTC/SRT/ABL REST API
- `LiveController.java` — streaming engine REST endpoints

### Frontend
- `WebCodecsSender` — WebCodecs VideoEncoder + GPU pipeline
- `WebCodecsPlayer` — WebCodecs VideoDecoder + WebRTC
- `live.ts` — HTTP API client

## ⑤ Unimplemented Interface Methods

| Module | Class | Method | Status |
|--------|-------|--------|--------|
| player | — | No `.cpp` file exists | ❌ Header-only, no implementation |
| encoder | NvEncEncoder | `codec_name()` | 🔶 Returns hardcoded string |
| network | WebRTCStreamer | `send_packet()` | ❌ Stub (returns true, no data sent) |
| network | SRTStreamer | `send_packet()` | ❌ Stub when ENABLE_SRT not defined |
| ai | ONNXModel | `load()` | ❌ Stub (returns true, no model loaded) |
| ai | TensorRTModel | `load()` | ❌ Stub (returns true, no engine built) |
| ai | TensorRTEngine | `build_engine()` / `infer()` | ❌ Stub (returns true, nothing runs) |
| gpu | VulkanPipeline | `process_frame()` | ❌ Stub (returns true, nothing processed) |
| capture | CameraCapture | platform inits | ❌ Stub (4 platform backends return true) |
| capture | CameraCapture | `read_frame_impl()` | ❌ Stub (no real frame capture) |
| server | StreamServer | `create_stream()` (non-SRS) | ❌ Stub (just cout + return true) |

## ⑥ TODO Found

**None.** Zero TODO comments in any C++ or Java file.

**Frontend exceptions:**
- `webcodecs_sender.ts` line 252: `// (simplified - in production use proper GPU pipeline)` — notes the GPU pipeline is a simplified stub

## ⑦ FIXME Found

**None.** Zero FIXME comments in the entire project.
