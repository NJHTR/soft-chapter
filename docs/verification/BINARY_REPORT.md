# STEP 3 — Binary Report

## Output Directory: `streaming-engine/build/`

### Static Libraries (.lib)

| File | Size | Exported Symbols | Dependencies |
|------|------|-----------------|--------------|
| `common/Release/streaming_engine_common.lib` | 164 KB | Frame, Pipeline, StreamingEngine | opencv_core (transitive) |
| `ai/Release/streaming_engine_ai.lib` | 577 KB | AIModel, TensorRTEngine, AIModelRegistry | streaming_engine_common |
| `gpu/Release/streaming_engine_gpu.lib` | 534 KB | GPUOversampler, GPUColorConverter, GPUMemoryPool, ShaderManager | streaming_engine_common, opengl32 |
| `beauty/Release/streaming_engine_beauty.lib` | 189 KB | BeautyPipeline | streaming_engine_ai, streaming_engine_common |
| `capture/Release/streaming_engine_capture.lib` | 423 KB | CameraCapture, FFmpegCapture | streaming_engine_common, opencv_imgproc, FFMPEG |
| `encoder/Release/streaming_engine_encoder.lib` | 524 KB | X264Encoder, BitrateController, AudioEncoder | streaming_engine_common, FFMPEG |
| `network/Release/streaming_engine_network.lib` | 1.04 MB | RTMPStreamer, FECEncoder, FECDecoder, NACKHandler, Muxer, NetworkPipe | streaming_engine_common, streaming_engine_encoder |
| `server/Release/streaming_engine_server.lib` | 348 KB | StreamServer, Transcoder | streaming_engine_common, streaming_engine_encoder, streaming_engine_network |

### Executables (.exe)

| File | Size | Type |
|------|------|------|
| `tests/Release/streaming_engine_tests.exe` | 53 KB | Console test executable |

### Dynamic Libraries (.dll) — Bundled for tests

| File | Size | Description |
|------|------|-------------|
| `avcodec-62.dll` | 13.1 MB | FFmpeg codec library |
| `avformat-62.dll` | 2.4 MB | FFmpeg format library |
| `avfilter-11.dll` | 3.7 MB | FFmpeg filter library |
| `avdevice-62.dll` | 72 KB | FFmpeg device library |
| `avutil-60.dll` | 856 KB | FFmpeg utility library |
| `swresample-6.dll` | 124 KB | FFmpeg audio resample |
| `swscale-9.dll` | 983 KB | FFmpeg image scaling |

### Key Observations

1. **No streaming_engine.dll exists.** The JNI bridge (`jni_bridge.h`) declares 7 native C functions, but:
   - No `.cpp` file implements these JNI functions
   - No CMake target produces a **shared library (.dll)** — only static libraries (.lib)
   - The Java code calls `System.loadLibrary("streaming_engine")` but no such DLL is ever built

2. **FFmpeg DLLs are only copied to the test output directory.** The test executable works because the DLLs are in its directory. For production, these FFmpeg DLLs would need to be deployed alongside the application.

3. **All C++ modules are static libraries (.lib).** This means they must be linked into a final executable or DLL. Currently only the test executable links them together.

4. **Total static library size: ~3.6 MB** — reasonable for a streaming engine core.

5. **Missing dependency check:** The test executable depends on all 7 FFmpeg DLLs. If any are missing at runtime, the test will fail with a DLL load error. In the current build, all 7 are present and the test passes.

## Missing Binaries

| Expected Binary | Status | Impact |
|-----------------|--------|--------|
| `streaming_engine.dll` | ❌ **Not built** | JNI bridge cannot be loaded by Java |
| `streaming_engine_tests.exe` | ✅ **Built** | Core tests pass |
| `streaming_engine_gpu.dll` | ❌ Not built (static lib only) | Must be linked into final binary |
| `streaming_engine_network.dll` | ❌ Not built (static lib only) | Must be linked into final binary |
