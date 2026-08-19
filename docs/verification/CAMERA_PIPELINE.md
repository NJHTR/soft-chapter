# STEP 4 — Camera Pipeline Verification

## Data Flow

```
[Physical Camera]
      │
      ▼
CameraCapture (camera_capture.cpp)
  ├─ init_mediafoundation()  →  Windows MF API (STUB — returns true)
  ├─ init_directshow()       →  Windows DShow API (STUB — returns true)
  ├─ init_avfoundation()     →  macOS AVF API (STUB — returns true)
  ├─ init_v4l2()             →  Linux V4L2 API (STUB — returns true)
  │
  ├─ init()  →  selects backend, sets config (REAL)
  └─ read_frame() / read_frame_impl()  →  (STUB — sets timestamp only, no real frame)
      │
      ▼
FFmpegCapture (ffmpeg_capture.cpp)
  ├─ av_find_input_format()        →  (REAL — calls FFmpeg)
  ├─ avformat_open_input()         →  (REAL — calls FFmpeg)  
  ├─ av_read_frame()               →  (REAL — decodes frames)
  ├─ avcodec_decode_video2()       →  (REAL — decodes packets)
  ├─ sws_scale()                   →  (REAL — converts to RGBA)
  └─ Output: std::shared_ptr<Frame>  →  RGB24 buffer (REAL)
      │
      ▼
capture_factory.cpp
  └─ create_capture(config)
       ├─ USB_Camera / DSLR / HDMI / Screen  →  CameraCapture (STUB)
       ├─ OBS / FFmpeg                       →  FFmpegCapture (REAL)
       ├─ MediaFoundation / DirectShow / V4L2 / AVFoundation  →  CameraCapture (STUB)
       └─ default                            →  nullptr
```

## Verification

| Component | Implementation | Status |
|-----------|---------------|--------|
| `CameraCapture` base class | `camera_capture.h` | ✅ Declared |
| `CameraCapture::init()` | `camera_capture.cpp:28` | ✅ Real — sets config, calls platform init |
| `CameraCapture::init_mediafoundation()` | `camera_capture.cpp:51` | ❌ **STUB** — just `cout + return true` |
| `CameraCapture::init_directshow()` | `camera_capture.cpp:61` | ❌ **STUB** — just `cout + return true` |
| `CameraCapture::init_avfoundation()` | `camera_capture.cpp:73` | ❌ **STUB** — just `cout + return true` |
| `CameraCapture::init_v4l2()` | `camera_capture.cpp:85` | ❌ **STUB** — just `cout + return true` |
| `CameraCapture::read_frame()` | `camera_capture.cpp:111` | ⚠️ Partial — sets timestamp but no real frame capture |
| `CameraCapture::read_frame_impl()` | `camera_capture.cpp:125` | ❌ **STUB** — no actual camera read |
| `CameraCapture::start()` | `camera_capture.cpp:99` | ✅ Real — opens device |
| `CameraCapture::stop()` | `camera_capture.cpp:105` | ✅ Real — closes device |
| `FFmpegCapture::init()` | `ffmpeg_capture.cpp:28` | ✅ Real — `avformat_open_input` |
| `FFmpegCapture::start()` | `ffmpeg_capture.cpp:88` | ✅ Real — capture loop thread |
| `FFmpegCapture::read_frame()` | `ffmpeg_capture.cpp:120` | ✅ Real — `av_read_frame` + decode + swscale |
| `capture_factory.cpp` | factory | ✅ Real — dispatches by source type |

## Conclusion

**Camera pipeline is only 50% functional:**
- `FFmpegCapture` works — it can capture from FFmpeg-supported sources (RTSP streams, files, avdevice)
- `CameraCapture` is **not functional** — all 4 platform backends are stubs
- The factory correctly selects `FFmpegCapture` for OBS/FFmpeg sources, but `CameraCapture` for all direct camera sources

**Runtime impact:** If a user tries to use a USB camera via `CaptureSource::USB_Camera`, the system falls into the `CameraCapture` path which does nothing.
