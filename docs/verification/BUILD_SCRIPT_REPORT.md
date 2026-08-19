# STEP 12 — Build Script Verification

## File: `build.bat` (7,577 bytes, 127 lines)

### What It Does

| Phase | Lines | Description |
|-------|-------|-------------|
| Header | 1-6 | `@echo off`, title "Douyin Streaming Engine Build" |
| Dep check | 8-33 | Docker detection only (not required) |
| vcpkg | 34-41 | Detects `vcpkg` in env or common locations |
| vcpkg install | 42-52 | Runs `vcpkg install --triplet x64-windows` for dependencies |
| Toolchain | 53-60 | Sets `CMAKE_TOOLCHAIN_FILE` |
| Feature detect | 61-91 | Checks CUDA, NVENC header, Vulkan SDK, ONNX |
| CMake configure | 93-100 | `cmake -B build -G "Visual Studio 18 2026"` with feature flags |
| CMake build | 101-107 | `cmake --build build --config Release` |
| Test | 108-114 | `ctest -C Release --output-on-failure` |
| Summary | 116-127 | Reports success/failure, lists flags |

### Feature Detection Logic

| Feature | Detection Method | Notes |
|---------|-----------------|-------|
| CUDA | `nvcc --version` via `where` | Checks PATH |
| NVENC | `nvEncodeAPI.h` in CUDA_PATH | Checks SDK header in CUDA include path |
| Vulkan | `VULKAN_SDK` env var | Checks environment variable |
| ONNX | `ONNX_RUNTIME_DIR` env var | Checks environment variable |
| SRT | vcpkg detection | Handled by CMake find_package |
| WebRTC | Always OFF | Hardcoded to OFF |

### Build Flags Generated

```
-DENABLE_CUDA=%CUDA_FOUND%
-DENABLE_NVENC=%NVENC_FOUND%
-DENABLE_VULKAN=%VULKAN_FOUND%
-DENABLE_TENSORRT=%CUDA_FOUND%  (same as CUDA)
-DENABLE_ONNX=%ONNX_FOUND%
-DENABLE_SRT=OFF  (WebRTC/SRT: disabled)
-DENABLE_WEBRTC=OFF
-DENABLE_TESTS=ON
-DENABLE_SERVER=ON
```

### Issues

| Issue | Severity | Description |
|-------|----------|-------------|
| Line 38: `vcpkg` detection via `where` | ⚠️ Warning | Works only if vcpkg is in PATH; no fallback for common install paths |
| Line 43: Installs ALL deps without `--keep-going` | ⚠️ Minor | One failure aborts entire install |
| Line 62: CUDA check in `%CUDA_PATH%` | ⚠️ Minor | Capitalization differs from NVIDIA's default `CUDA_PATH` (case-insensitive on Windows) |
| Line 85: No Vulkan fallback env vars | ⚠️ Minor | Only checks `VULKAN_SDK`, not `VK_SDK_PATH` |
| No `--clean-first` | ⚠️ Minor | Incremental builds may cache old flags |
| No `cmake --build` parallel flag (`/m`) | ⚠️ Minor | Slower builds on multi-core machines |
| All optional features default OFF | ✅ Correct | Conservative approach |

### Execution Result (This Session)

| Step | Duration | Status |
|------|----------|--------|
| vcpkg install | >40 min | ✅ Completed (19 packages) |
| cmake configure | ~30 sec | ✅ Completed |
| cmake build (Release) | ~2 min | ✅ Completed |
| ctest | 0.07 sec | ✅ Passed (1 test) |

### Verdict

**build.bat is functional and well-structured.** It correctly:
- Detects available SDKs
- Falls back when SDKs are missing
- Generates correct CMake flags
- Produces a successful build
- Runs tests

The script is conservative by design (all extras OFF by default). To enable GPU acceleration, the user must install CUDA Toolkit, Vulkan SDK, etc.
