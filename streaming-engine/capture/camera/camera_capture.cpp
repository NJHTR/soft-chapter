#include "capture/camera/camera_capture.h"
#include <iostream>
#include <thread>
#include <atomic>
#include <cstring>

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mfreadwrite.h>
#include <comdef.h>
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "mfuuid.lib")
#pragma comment(lib, "mfreadwrite.lib")
#pragma comment(lib, "ole32.lib")
#pragma comment(lib, "oleaut32.lib")
#endif

class CameraCapture::Impl {
public:
    CaptureConfig config_;
    std::atomic<bool> running_{false};
    FrameCallback frame_cb_;
    std::thread capture_thread_;

#ifdef _WIN32
    IMFSourceReader* source_reader_ = nullptr;
    int actual_width_ = 0;
    int actual_height_ = 0;
    int actual_fps_ = 30;
#endif

    bool init(const CaptureConfig& config) {
        config_ = config;
        std::cout << "CameraCapture initializing: "
                  << config_.width << "x" << config_.height
                  << " @ " << config_.fps << "fps"
                  << " (oversample: " << (config_.use_oversampling ? "yes" : "no") << ")"
                  << std::endl;

#ifdef _WIN32
        return init_mediafoundation();
#elif __APPLE__
        return init_avfoundation();
#elif __linux__
        return init_v4l2();
#else
        std::cerr << "CameraCapture: unsupported platform" << std::endl;
        return false;
#endif
    }

#ifdef _WIN32
    bool init_mediafoundation() {
        HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        if (FAILED(hr)) {
            std::cerr << "  CoInitializeEx failed: 0x" << std::hex << hr << std::dec << std::endl;
            return false;
        }

        hr = MFStartup(MF_VERSION);
        if (FAILED(hr)) {
            std::cerr << "  MFStartup failed: 0x" << std::hex << hr << std::dec << std::endl;
            CoUninitialize();
            return false;
        }

        IMFAttributes* pAttributes = nullptr;
        hr = MFCreateAttributes(&pAttributes, 1);
        if (FAILED(hr)) { MFShutdown(); CoUninitialize(); return false; }

        hr = pAttributes->SetGUID(
            MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE,
            MF_DEVSOURCE_ATTRIBUTE_SOURCE_TYPE_VIDCAP_GUID);
        if (FAILED(hr)) { pAttributes->Release(); MFShutdown(); CoUninitialize(); return false; }

        IMFActivate** ppDevices = nullptr;
        UINT32 deviceCount = 0;
        hr = MFEnumDeviceSources(pAttributes, &ppDevices, &deviceCount);
        pAttributes->Release();

        if (FAILED(hr) || deviceCount == 0) {
            std::cerr << "  No video capture devices found" << std::endl;
            MFShutdown();
            CoUninitialize();
            return false;
        }

        std::cout << "  Found " << deviceCount << " video device(s)" << std::endl;

        WCHAR* friendlyName = nullptr;
        UINT32 nameLen = 0;
        ppDevices[0]->GetAllocatedString(
            MF_DEVSOURCE_ATTRIBUTE_FRIENDLY_NAME, &friendlyName, &nameLen);
        if (friendlyName) {
            char buf[256] = {};
            WideCharToMultiByte(CP_UTF8, 0, friendlyName, -1, buf, sizeof(buf), nullptr, nullptr);
            std::cout << "  Using device: " << buf << std::endl;
            CoTaskMemFree(friendlyName);
        }

        IMFMediaSource* pMediaSource = nullptr;
        hr = ppDevices[0]->ActivateObject(IID_PPV_ARGS(&pMediaSource));
        for (UINT32 i = 0; i < deviceCount; i++) ppDevices[i]->Release();
        CoTaskMemFree(ppDevices);

        if (FAILED(hr)) {
            std::cerr << "  ActivateObject failed: 0x" << std::hex << hr << std::dec << std::endl;
            MFShutdown(); CoUninitialize();
            return false;
        }

        IMFAttributes* pReaderAttr = nullptr;
        hr = MFCreateAttributes(&pReaderAttr, 1);
        if (SUCCEEDED(hr)) {
            pReaderAttr->SetUINT32(MF_READWRITE_ENABLE_HARDWARE_TRANSFORMS, 1);
        }

        hr = MFCreateSourceReaderFromMediaSource(
            pMediaSource, pReaderAttr, &source_reader_);
        pMediaSource->Release();
        if (pReaderAttr) pReaderAttr->Release();

        if (FAILED(hr)) {
            std::cerr << "  MFCreateSourceReaderFromMediaSource failed: 0x"
                      << std::hex << hr << std::dec << std::endl;
            MFShutdown(); CoUninitialize();
            return false;
        }

        // Try to set desired media type: NV12 at requested resolution
        IMFMediaType* pDesiredType = nullptr;
        MFCreateMediaType(&pDesiredType);
        pDesiredType->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
        pDesiredType->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_NV12);

        int try_width = config_.width;
        int try_height = config_.height;
        if (try_width > 1920 || try_height > 1080) {
            try_width = 1280;
            try_height = 720;
        }
        MFSetAttributeSize(pDesiredType, MF_MT_FRAME_SIZE, try_width, try_height);
        MFSetAttributeRatio(pDesiredType, MF_MT_FRAME_RATE, config_.fps, 1);

        hr = source_reader_->SetCurrentMediaType(
            MF_SOURCE_READER_FIRST_VIDEO_STREAM, nullptr, pDesiredType);
        pDesiredType->Release();

        if (FAILED(hr)) {
            hr = source_reader_->SetCurrentMediaType(
                MF_SOURCE_READER_FIRST_VIDEO_STREAM, nullptr, nullptr);
        }

        // Read back the actual media type
        IMFMediaType* pActualType = nullptr;
        hr = source_reader_->GetCurrentMediaType(
            MF_SOURCE_READER_FIRST_VIDEO_STREAM, &pActualType);
        if (SUCCEEDED(hr)) {
            GUID subtype = {};
            pActualType->GetGUID(MF_MT_SUBTYPE, &subtype);
            MFGetAttributeSize(pActualType, MF_MT_FRAME_SIZE,
                               (UINT32*)&actual_width_, (UINT32*)&actual_height_);

            UINT32 num = 0, den = 0;
            MFGetAttributeRatio(pActualType, MF_MT_FRAME_RATE, &num, &den);
            if (den > 0) actual_fps_ = (int)(num / den);

            char subtypeStr[40] = {};
            snprintf(subtypeStr, sizeof(subtypeStr),
                "%08x-%04x-%04x-%02x%02x-%02x%02x%02x%02x%02x%02x",
                subtype.Data1, subtype.Data2, subtype.Data3,
                subtype.Data4[0], subtype.Data4[1], subtype.Data4[2], subtype.Data4[3],
                subtype.Data4[4], subtype.Data4[5], subtype.Data4[6], subtype.Data4[7]);
            std::cout << "  Actual format: " << actual_width_ << "x" << actual_height_
                      << " @" << actual_fps_ << "fps, subtype=" << subtypeStr << std::endl;
            pActualType->Release();
        }

        std::cout << "  MediaFoundation camera initialized" << std::endl;
        return true;
    }
#endif

    bool start() {
        if (running_) return true;

        running_ = true;
        int target_fps = config_.fps;
        if (target_fps <= 0 || target_fps > 60) target_fps = actual_fps_ > 0 ? actual_fps_ : 30;

        capture_thread_ = std::thread([this, target_fps]() {
            CoInitializeEx(nullptr, COINIT_MULTITHREADED);
            while (running_) {
                auto frame = Frame::create_cpu(
                    actual_width_ > 0 ? actual_width_ : config_.width,
                    actual_height_ > 0 ? actual_height_ : config_.height,
                    PixelFormat::NV12);

                if (!frame) continue;

                if (!read_frame_impl(frame)) continue;

                if (frame_cb_) {
                    frame_cb_(frame);
                }

                std::this_thread::sleep_for(
                    std::chrono::milliseconds(1000 / target_fps));
            }
            CoUninitialize();
        });

        return true;
    }

    bool read_frame_impl(std::shared_ptr<Frame>& frame) {
#ifdef _WIN32
        if (!source_reader_) return false;

        DWORD streamIndex = MF_SOURCE_READER_FIRST_VIDEO_STREAM;
        DWORD flags = 0;
        LONGLONG llTimestamp = 0;
        IMFSample* pSample = nullptr;

        HRESULT hr = source_reader_->ReadSample(
            MF_SOURCE_READER_FIRST_VIDEO_STREAM,
            0,
            &streamIndex,
            &flags,
            &llTimestamp,
            &pSample);

        if (FAILED(hr)) return false;

        if (flags & MF_SOURCE_READERF_STREAMTICK) {
            if (pSample) pSample->Release();
            return false;
        }

        if (!pSample) return false;

        IMFMediaBuffer* pBuffer = nullptr;
        hr = pSample->ConvertToContiguousBuffer(&pBuffer);
        if (FAILED(hr)) { pSample->Release(); return false; }

        BYTE* pData = nullptr;
        DWORD maxLen = 0, curLen = 0;
        hr = pBuffer->Lock(&pData, &maxLen, &curLen);
        if (FAILED(hr)) { pBuffer->Release(); pSample->Release(); return false; }

        auto& buf = frame->buffer();
        int w = buf.width;
        int h = buf.height;
        size_t y_size = w * h;
        size_t uv_size = w * h / 2;
        size_t expected = y_size + uv_size;

        if (curLen >= expected) {
            memcpy(buf.data, pData, y_size);
            memcpy(buf.data + y_size, pData + y_size, uv_size);
        } else if (curLen >= y_size) {
            memcpy(buf.data, pData, y_size);
            size_t copy_uv = (std::min)((size_t)curLen - y_size, uv_size);
            if (copy_uv > 0) memcpy(buf.data + y_size, pData + y_size, copy_uv);
        } else {
            pBuffer->Unlock();
            pBuffer->Release();
            pSample->Release();
            return false;
        }

        buf.timestamp = llTimestamp / 10000;
        buf.frame_index++;

        pBuffer->Unlock();
        pBuffer->Release();
        pSample->Release();
        return true;
#else
        frame->buffer().timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
        return true;
#endif
    }

    bool stop() {
        running_ = false;
        if (capture_thread_.joinable()) {
            capture_thread_.join();
        }

#ifdef _WIN32
        if (source_reader_) {
            source_reader_->Release();
            source_reader_ = nullptr;
        }
        MFShutdown();
        CoUninitialize();
#endif
        return true;
    }

    std::string device_name() const {
        return "Camera (" + std::to_string(actual_width_ > 0 ? actual_width_ : config_.width) + "x" +
               std::to_string(actual_height_ > 0 ? actual_height_ : config_.height) + ")";
    }
};

CameraCapture::CameraCapture() : impl_(std::make_unique<Impl>()) {}
CameraCapture::~CameraCapture() = default;

bool CameraCapture::init(const CaptureConfig& config) { return impl_->init(config); }
bool CameraCapture::start() { return impl_->start(); }
bool CameraCapture::stop() { return impl_->stop(); }
bool CameraCapture::read_frame(std::shared_ptr<Frame>& frame) {
    return impl_->read_frame_impl(frame);
}
void CameraCapture::set_frame_callback(FrameCallback cb) { impl_->frame_cb_ = std::move(cb); }
std::string CameraCapture::device_name() const { return impl_->device_name(); }
