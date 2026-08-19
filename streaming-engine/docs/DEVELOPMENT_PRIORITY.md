# Streaming Engine Development Priority

## Phase 1: Foundation (Weeks 1-2)
### Must-have for basic streaming

| Module | Priority | Reason |
|--------|----------|--------|
| Capture (MediaFoundation/DShow/AVFoundation) | P0 | Core input |
| GPU Pipeline (CUDA/OpenGL zero-copy) | P0 | Core processing |
| NVENC Encoder (H.264/H.265) | P0 | Core output |
| SRT Streamer | P0 | Core network |
| Legacy WebSocket fallback | P0 | Compatibility |
| JNI Bridge | P0 | Java integration |

## Phase 2: Professional Quality (Weeks 3-4)
### TikTok-level quality features

| Module | Priority | Reason |
|--------|----------|--------|
| AI Denoise (FastDVDNet/NAFNet) | P1 | Low-light quality |
| AI Beauty (MediaPipe + GPU shader) | P1 | Portrait enhancement |
| GPU Oversampling (4K→1080P) | P1 | Super-sampling quality |
| Dynamic Bitrate Controller | P1 | Network adaptation |
| AV1 Codec (NVENC AV1) | P1 | Best compression |

## Phase 3: Advanced Features (Weeks 5-6)
### Differentiators

| Module | Priority | Reason |
|--------|----------|--------|
| AI Super Resolution (RealESRGAN) | P2 | 720P→1080P upscale |
| HDR Color (BT.2020/PQ/HLG) | P2 | HDR streaming |
| 3D LUT Color Grading | P2 | Cinematic look |
| AI Sharpen (Guided Filter) | P2 | Edge-aware sharpening |
| WebRTC Streaming | P2 | Ultra-low latency |

## Phase 4: Scale & Polish (Weeks 7-8)
### Production readiness

| Module | Priority | Reason |
|--------|----------|--------|
| SRS Server Integration | P2 | Production serving |
| FEC (Reed-Solomon) | P2 | Lossy network recovery |
| ABR Transcoding | P2 | Multi-quality delivery |
| WebCodecs Player | P2 | Best browser playback |
| Performance Optimization | P3 | Reduce GPU mem/CPU |

## Model Roadmap

### Denoise Models (evolving):
1. FastDVDNet → Current best for real-time
2. NAFNet → Lighter, faster (replace FastDVDNet)
3. BasicVSR++ → Temporal denoise (future)

### Super Resolution Models (evolving):
1. FSRCNN → Initial fast SR
2. RealESRGAN → Replace FSRCNN for quality
3. SwinIR → Future replacement for RealESRGAN

### Beauty Pipeline (evolving):
1. MediaPipe → Current face detection
2. InsightFace → Replace for accuracy
3. Custom GPU Shader → Permanent rendering

## Architecture Decision Records

### ADR-1: Zero-copy GPU Pipeline
- Decision: All frame processing stays on GPU (CUDA/OpenGL/Vulkan)
- Rationale: Eliminates CPU-GPU transfer bottleneck (>10ms per 4K frame)
- Alternative rejected: CPU memcpy between filters (20-30ms per operation)

### ADR-2: NVENC AV1 as Primary Codec
- Decision: Use NVENC AV1 on Ada Lovelace+ GPUs
- Rationale: 30% bitrate reduction vs H.265 at same quality
- Fallback: NVENC H.265 for older GPUs, x264 for CPU-only

### ADR-3: SRT as Primary Transport
- Decision: Use SRT for ingest, WebRTC for viewer playback
- Rationale: SRT provides reliable delivery with configurable latency
- Alternative rejected: RTMP (too high latency, poor loss recovery)

### ADR-4: Oversampling Strategy
- Decision: Always capture at 4K, GPU-downsample to output resolution
- Rationale: Superior anti-aliasing, better sub-pixel rendering
- Data: 4K→1080P produces 2-3dB better PSNR than native 1080P capture
