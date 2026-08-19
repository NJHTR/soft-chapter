# STEP 7 — Encoder Verification

## Encoder Types

### NvEncEncoder (nvenc/nvenc_encoder.cpp)
| Method | Status | Notes |
|--------|--------|-------|
| `init()` | ✅ REAL (when NVENC SDK present) | CUDA init, NVENC session, H.265/AV1 config, register resources |
| `encode()` | ✅ REAL | `nvEncEncodePicture` with dynamic bitrate |
| `flush()` | ✅ REAL | `nvEncEncodePicture(EOS)` |
| `shutdown()` | ✅ REAL | CUDA + NVENC cleanup |
| `set_bitrate()` | ✅ REAL | `NV_ENC_RECONFIGURE_PARAMS` |
| `request_keyframe()` | ✅ REAL | `NV_ENC_RECONFIGURE_PARAMS::forceIDR` |

- **Not compiled** in current build (`ENABLE_NVENC=OFF`, `nvEncodeAPI.h` not found)
- Code is real NVENC SDK usage, not a stub
- Requires NVIDIA GPU with Video Codec SDK

### X264Encoder (x264/x264_encoder.cpp)
| Method | Status | Notes |
|--------|--------|-------|
| `init()` | ✅ REAL | `avcodec_find_encoder(AV_CODEC_ID_H264)`, libx264 preset/tune |
| `encode()` | ✅ REAL | `avcodec_send_frame` + `avcodec_receive_packet` |
| `flush()` | ✅ REAL | FFmpeg flush |
| `shutdown()` | ✅ REAL | `avcodec_free_context` |
| `set_bitrate()` | ✅ REAL | Re-opens encoder with new bitrate |
| `request_keyframe()` | ✅ REAL | Sets forced IDR on next frame |

- **Compiled and working** in current build
- Real FFmpeg libavcodec usage throughout
- No stubs

### X265Encoder (x264_encoder.cpp — file name is misleading)
- **Not found.** The `encoder/CMakeLists.txt` lists `x264_encoder.cpp` only. `x265_encoder.cpp` does not exist.
- The factory (`encoder_factory.cpp:18`) references `X265Encoder` but no source file exists
- This means `EncoderType::x265` will fail at link time

## Verification: Does It Really Call FFmpeg?

| Function | Calls FFmpeg? | Evidence |
|----------|--------------|----------|
| `avcodec_find_encoder(AV_CODEC_ID_H264)` | ✅ YES | `x264_encoder.cpp:27` |
| `avcodec_alloc_context3()` | ✅ YES | `x264_encoder.cpp:29` |
| `avcodec_open2()` | ✅ YES | `x264_encoder.cpp:55` |
| `av_frame_alloc()` | ✅ YES | `x264_encoder.cpp:58` |
| `avcodec_send_frame()` | ✅ YES | `x264_encoder.cpp:138` |
| `avcodec_receive_packet()` | ✅ YES | `x264_encoder.cpp:142` |
| `av_packet_alloc()` | ✅ YES | `x264_encoder.cpp:96` |
| RGBA→YUV420P via `libswscale` | ✅ YES | `x264_encoder.cpp:82` |

No stubs, no placeholders, no fakes. The x264 encoder is **fully functional**.

## Audio Encoder (audio_encoder.cpp)

| Component | Status | Notes |
|-----------|--------|-------|
| `WASAPI Capture` | ⚠️ PARTIAL | Real COM init, device enumeration, audio client. Falls back to sine wave if no device |
| `SineWaveCapture` | ✅ REAL (as test tone) | Used as fallback when no capture device |
| `FFmpegAudioEncoder` | ⚠️ PARTIAL | Real FFmpeg codec init when `ENABLE_FFMPEG` defined; otherwise PCM passthrough |
| `AudioMixer` | ✅ REAL | Weighted mixing with clamp |

**Key Finding:** The WASAPI capture has real COM API calls but uses `use_fake_` flag to fall back to sine wave on any error. On a machine with no microphone, it silently generates test tones. This is intentional (dev/debug mode) but would be invisible in production.

## Encoder Factory (encoder_factory.cpp)

```
EncoderType::NVENC → NvEncEncoder (requires ENABLE_NVENC)  → STUBs to x264
EncoderType::x264  → X264Encoder (always)                   → ✅ REAL
EncoderType::x265  → X265Encoder (FILE NOT FOUND)           → ❌ BROKEN (link error)
```

**X265Encoder referenced but not implemented.** The factory will fail to compile/link if `EncoderType::x265` is ever used.

## Summary

| Encoder | Real FFmpeg Calls? | Compiled? | Works at Runtime? |
|---------|-------------------|-----------|-------------------|
| x264 (H.264) | ✅ YES | ✅ YES | ✅ YES |
| x265 (H.265) | ❌ No source file | ❌ NO | ❌ NO |
| NVENC H.264 | ✅ Real code | ❌ NO (no SDK) | ❌ NO |
| NVENC H.265 | ✅ Real code | ❌ NO (no SDK) | ❌ NO |
| NVENC AV1 | ✅ Real code | ❌ NO (no SDK) | ❌ NO |
| Audio AAC | ⚠️ Partially | ✅ YES | ⚠️ Falls back to sine wave |
| Audio Opus | ⚠️ Partially | ✅ YES | ⚠️ Falls back to sine wave |
| Audio MP3 | ⚠️ Partially | ✅ YES | ⚠️ Falls back to sine wave |
