#ifdef _WIN32
#define NOMINMAX
#include <windows.h>
#include <mmeapi.h>
#include <audioclient.h>
#include <mmdeviceapi.h>
#include <functiondiscovery.h>
#endif

#include "encoder/audio_encoder.h"
#include <iostream>
#include <cstring>
#include <cmath>

// ==================== WASAPI Audio Capture (Windows) ====================
#ifdef _WIN32
class WasapiCapture : public AudioCapture {
public:
    WasapiCapture() = default;
    ~WasapiCapture() override { stop(); }

    bool init(int sample_rate, int channels) override {
        sample_rate_ = sample_rate;
        channels_ = channels;
        // Initialize COM
        HRESULT hr = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        if (FAILED(hr) && hr != RPC_E_CHANGED_MODE) {
            std::cerr << "[Audio] COM init failed: " << std::hex << hr << std::endl;
            return false;
        }

        // Get default audio capture device
        IMMDeviceEnumerator* enumerator = nullptr;
        hr = CoCreateInstance(__uuidof(MMDeviceEnumerator), nullptr,
                              CLSCTX_ALL, __uuidof(IMMDeviceEnumerator),
                              (void**)&enumerator);
        if (FAILED(hr)) {
            std::cerr << "[Audio] Failed to create device enumerator" << std::endl;
            return false;
        }

        IMMDevice* device = nullptr;
        hr = enumerator->GetDefaultAudioEndpoint(eCapture, eCommunications, &device);
        if (FAILED(hr)) {
            hr = enumerator->GetDefaultAudioEndpoint(eCapture, eConsole, &device);
        }
        enumerator->Release();

        if (FAILED(hr) || !device) {
            // Fallback: use sine wave generator
            std::cerr << "[Audio] No capture device, using fake audio" << std::endl;
            use_fake_ = true;
            return true;
        }

        // Activate audio client
        hr = device->Activate(__uuidof(IAudioClient), CLSCTX_ALL,
                              nullptr, (void**)&audio_client_);
        device->Release();

        if (FAILED(hr)) {
            std::cerr << "[Audio] Failed to activate audio client" << std::endl;
            use_fake_ = true;
            return true;
        }

        // Configure format
        WAVEFORMATEX format;
        format.wFormatTag = WAVE_FORMAT_IEEE_FLOAT;
        format.nChannels = static_cast<WORD>(channels);
        format.nSamplesPerSec = sample_rate;
        format.wBitsPerSample = 32;
        format.nBlockAlign = format.nChannels * (format.wBitsPerSample / 8);
        format.nAvgBytesPerSec = format.nSamplesPerSec * format.nBlockAlign;
        format.cbSize = 0;

        hr = audio_client_->Initialize(
            AUDCLNT_SHAREMODE_SHARED,
            AUDCLNT_STREAMFLAGS_EVENTCALLBACK,
            10000000,  // 1 second buffer
            0,
            &format,
            nullptr);

        if (FAILED(hr)) {
            std::cerr << "[Audio] Failed to initialize audio client: "
                      << std::hex << hr << std::endl;
            audio_client_->Release();
            audio_client_ = nullptr;
            use_fake_ = true;
            return true;
        }

        // Get capture client
        hr = audio_client_->GetService(
            __uuidof(IAudioCaptureClient),
            (void**)&capture_client_);

        if (FAILED(hr)) {
            std::cerr << "[Audio] Failed to get capture client" << std::endl;
            audio_client_->Release();
            audio_client_ = nullptr;
            use_fake_ = true;
            return true;
        }

        // Create event for notifications
        capture_event_ = CreateEvent(nullptr, FALSE, FALSE, nullptr);

        hr = audio_client_->SetEventHandle(capture_event_);
        if (FAILED(hr)) {
            std::cerr << "[Audio] Failed to set event handle" << std::endl;
        }

        // Get buffer size
        UINT32 buffer_frames = 0;
        audio_client_->GetBufferSize(&buffer_frames);
        buffer_size_ = buffer_frames;

        initialized_ = true;
        return true;
    }

    bool start() override {
        if (use_fake_) return true;
        if (!audio_client_) return false;
        HRESULT hr = audio_client_->Start();
        if (FAILED(hr)) {
            std::cerr << "[Audio] Failed to start capture" << std::endl;
            return false;
        }
        running_ = true;
        return true;
    }

    bool stop() override {
        if (audio_client_ && running_) {
            audio_client_->Stop();
        }
        running_ = false;
        return true;
    }

    bool read_frame(AudioFrame& frame) override {
        if (use_fake_) {
            // Generate test tone (440Hz sine wave)
            int num_samples = frame_size_;
            frame.samples.resize(num_samples * channels_);
            frame.sample_rate = sample_rate_;
            frame.channels = channels_;
            frame.timestamp = fake_ts_;
            frame.frame_index = fake_index_++;

            for (int i = 0; i < num_samples; i++) {
                float t = static_cast<float>(fake_ts_ + i * 1000000 / sample_rate_)
                          / 1000000.0f;
                float sample = sinf(2.0f * 3.14159265f * 440.0f * t) * 0.3f;
                for (int c = 0; c < channels_; c++) {
                    frame.samples[i * channels_ + c] = sample;
                }
            }
            fake_ts_ += num_samples * 1000000 / sample_rate_;
            return true;
        }

        if (!capture_client_) return false;

        WaitForSingleObject(capture_event_, 1000);

        UINT32 packets = 0;
        capture_client_->GetNextPacketSize(&packets);

        if (packets == 0) return false;

        BYTE* data = nullptr;
        UINT32 frames_available = 0;
        DWORD flags = 0;

        HRESULT hr = capture_client_->GetBuffer(
            &data, &frames_available, &flags, nullptr, nullptr);
        if (FAILED(hr) || !data || frames_available == 0) {
            return false;
        }

        frame.samples.resize(frames_available * channels_);
        frame.sample_rate = sample_rate_;
        frame.channels = channels_;
        frame.timestamp = fake_ts_;
        frame.frame_index = fake_index_++;

        memcpy(frame.samples.data(), data,
               frames_available * channels_ * sizeof(float));

        fake_ts_ += static_cast<int64_t>(frames_available)
                    * 1000000 / sample_rate_;

        capture_client_->ReleaseBuffer(frames_available);
        return true;
    }

    void set_callback(AudioCallback cb) override {
        callback_ = std::move(cb);
    }

private:
    IAudioClient* audio_client_ = nullptr;
    IAudioCaptureClient* capture_client_ = nullptr;
    HANDLE capture_event_ = nullptr;
    int sample_rate_ = 48000;
    int channels_ = 2;
    int frame_size_ = 1024;
    int64_t fake_ts_ = 0;
    int fake_index_ = 0;
    bool initialized_ = false;
    bool running_ = false;
    bool use_fake_ = false;
    UINT32 buffer_size_ = 0;
    AudioCallback callback_;
};
#endif

// ==================== Fallback / Cross-platform capture ====================
class SineWaveCapture : public AudioCapture {
public:
    bool init(int sample_rate, int channels) override {
        sample_rate_ = sample_rate;
        channels_ = channels;
        return true;
    }
    bool start() override { return true; }
    bool stop() override { return true; }
    bool read_frame(AudioFrame& frame) override {
        int num_samples = frame_size_;
        frame.samples.resize(num_samples * channels_);
        frame.sample_rate = sample_rate_;
        frame.channels = channels_;
        frame.timestamp = fake_ts_;
        frame.frame_index = fake_index_++;

        for (int i = 0; i < num_samples; i++) {
            float t = static_cast<float>(fake_ts_ + i * 1000000 / sample_rate_)
                      / 1000000.0f;
            float sample = sinf(2.0f * 3.14159265f * 440.0f * t) * 0.3f;
            for (int c = 0; c < channels_; c++) {
                frame.samples[i * channels_ + c] = sample;
            }
        }
        fake_ts_ += num_samples * 1000000 / sample_rate_;
        return true;
    }
    void set_callback(AudioCallback cb) override {}

private:
    int sample_rate_ = 48000;
    int channels_ = 2;
    int frame_size_ = 1024;
    int64_t fake_ts_ = 0;
    int fake_index_ = 0;
};

// ==================== FFmpeg Audio Encoder ====================
class FFmpegAudioEncoder : public AudioEncoder {
public:
    FFmpegAudioEncoder() = default;
    ~FFmpegAudioEncoder() override { shutdown(); }

    bool init(const AudioConfig& config) override {
        config_ = config;

#ifdef ENABLE_FFMPEG
        // Find encoder
        const AVCodec* codec = nullptr;
        switch (config.codec) {
            case AudioCodec::AAC:
                codec = avcodec_find_encoder(AV_CODEC_ID_AAC);
                break;
            case AudioCodec::Opus:
                codec = avcodec_find_encoder(AV_CODEC_ID_OPUS);
                break;
            case AudioCodec::MP3:
                codec = avcodec_find_encoder(AV_CODEC_ID_MP3);
                break;
            default:
                break;
        }

        if (!codec) {
            std::cerr << "[AudioEncoder] Codec not found" << std::endl;
            return false;
        }

        codec_context_ = avcodec_alloc_context3(codec);
        if (!codec_context_) return false;

        codec_context_->sample_fmt = AV_SAMPLE_FLT;
        codec_context_->sample_rate = config.sample_rate;
        codec_context_->channel_layout = config.channels == 2
            ? AV_CH_LAYOUT_STEREO : AV_CH_LAYOUT_MONO;
        codec_context_->channels = config.channels;
        codec_context_->bit_rate = config.bitrate;
        codec_context_->strict_std_compliance = FF_COMPLIANCE_EXPERIMENTAL;

        if (config.codec == AudioCodec::Opus) {
            codec_context_->cutoff = 20000;
            codec_context_->compression_level = 10;
        }

        if (avcodec_open2(codec_context_, codec, nullptr) < 0) {
            std::cerr << "[AudioEncoder] Failed to open codec" << std::endl;
            avcodec_free_context(&codec_context_);
            return false;
        }

        // Allocate frame
        frame_ = av_frame_alloc();
        frame_->nb_samples = config.frame_size;
        frame_->format = AV_SAMPLE_FLT;
        frame_->sample_rate = config.sample_rate;
        frame_->channel_layout = codec_context_->channel_layout;
        frame_->channels = config.channels;

        if (av_frame_get_buffer(frame_, 0) < 0) {
            std::cerr << "[AudioEncoder] Failed to allocate frame buffer"
                      << std::endl;
            avcodec_free_context(&codec_context_);
            av_frame_free(&frame_);
            return false;
        }

        // Allocate packet
        packet_ = av_packet_alloc();
        if (!packet_) {
            avcodec_free_context(&codec_context_);
            av_frame_free(&frame_);
            return false;
        }

        initialized_ = true;
        return true;
#else
        std::cerr << "[AudioEncoder] FFmpeg not enabled, using passthrough"
                  << std::endl;
        initialized_ = true;
        return true;
#endif
    }

    bool encode(const AudioFrame& input, std::vector<uint8_t>& output) override {
        output.clear();

#ifdef ENABLE_FFMPEG
        if (!initialized_ || !codec_context_) {
            // Passthrough: just copy raw PCM
            output.resize(input.samples.size() * sizeof(float));
            memcpy(output.data(), input.samples.data(),
                   input.samples.size() * sizeof(float));
            return true;
        }

        // Copy input to AVFrame
        uint8_t** planes = frame_->extended_data;
        int bytes_per_sample = av_get_bytes_per_sample(AV_SAMPLE_FLT);
        int frame_bytes = input.samples.size() * bytes_per_sample;
        memcpy(planes[0], input.samples.data(), frame_bytes);

        frame_->pts = input.frame_index;

        // Send frame to encoder
        int ret = avcodec_send_frame(codec_context_, frame_);
        if (ret < 0) {
            std::cerr << "[AudioEncoder] avcodec_send_frame error: " << ret
                      << std::endl;
            return false;
        }

        // Receive packets
        while (ret >= 0) {
            ret = avcodec_receive_packet(codec_context_, packet_);
            if (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) break;
            if (ret < 0) {
                std::cerr << "[AudioEncoder] avcodec_receive_packet error: "
                          << ret << std::endl;
                return false;
            }

            // Copy to output
            size_t offset = output.size();
            output.resize(offset + packet_->size);
            memcpy(output.data() + offset, packet_->data, packet_->size);

            if (callback_) {
                std::vector<uint8_t> pkt(packet_->data,
                                         packet_->data + packet_->size);
                callback_(std::move(pkt), packet_->pts * 1000000
                          / config_.sample_rate);
            }

            av_packet_unref(packet_);
        }

        return true;
#else
        output.resize(input.samples.size() * sizeof(float));
        memcpy(output.data(), input.samples.data(),
               input.samples.size() * sizeof(float));
        return true;
#endif
    }

    bool flush(std::vector<uint8_t>& output) override {
        output.clear();
#ifdef ENABLE_FFMPEG
        if (!codec_context_) return false;

        int ret = avcodec_send_frame(codec_context_, nullptr);
        if (ret < 0) return false;

        while (ret >= 0) {
            ret = avcodec_receive_packet(codec_context_, packet_);
            if (ret == AVERROR_EOF) break;
            if (ret < 0) break;

            size_t offset = output.size();
            output.resize(offset + packet_->size);
            memcpy(output.data() + offset, packet_->data, packet_->size);
            av_packet_unref(packet_);
        }
#endif
        return true;
    }

    void shutdown() override {
#ifdef ENABLE_FFMPEG
        if (codec_context_) {
            avcodec_free_context(&codec_context_);
        }
        if (frame_) {
            av_frame_free(&frame_);
        }
        if (packet_) {
            av_packet_free(&packet_);
        }
#endif
        initialized_ = false;
    }

    void set_output_callback(EncodedCallback cb) override {
        callback_ = std::move(cb);
    }

    AudioCodec codec() const override { return config_.codec; }

private:
    AudioConfig config_;
    bool initialized_ = false;
#ifdef ENABLE_FFMPEG
    AVCodecContext* codec_context_ = nullptr;
    AVFrame* frame_ = nullptr;
    AVPacket* packet_ = nullptr;
#endif
    EncodedCallback callback_;
};

// ==================== Factory ====================
std::unique_ptr<AudioCapture> create_audio_capture() {
#ifdef _WIN32
    auto* cap = new WasapiCapture();
    if (cap->init(48000, 2)) {
        return std::unique_ptr<AudioCapture>(cap);
    }
    delete cap;
#endif
    return std::make_unique<SineWaveCapture>();
}

std::unique_ptr<AudioEncoder> create_audio_encoder(const AudioConfig& config) {
    auto encoder = std::make_unique<FFmpegAudioEncoder>();
    if (encoder->init(config)) {
        return encoder;
    }
    return nullptr;
}

// ==================== AudioMixer ====================
class AudioMixer::Impl {
public:
    int sample_rate_ = 48000;
    int channels_ = 2;
    std::vector<float> volumes_;

    void mix(std::vector<AudioFrame>& sources, AudioFrame& output) {
        if (sources.empty()) {
            output.samples.clear();
            return;
        }

        // Use the longest source for output size
        size_t max_samples = 0;
        for (auto& src : sources) {
            if (src.samples.size() > max_samples) {
                max_samples = src.samples.size();
            }
        }

        output.samples.assign(max_samples, 0.0f);
        output.sample_rate = sample_rate_;
        output.channels = channels_;

        for (size_t i = 0; i < sources.size(); i++) {
            float vol = (i < volumes_.size()) ? volumes_[i] : 1.0f;
            auto& src = sources[i].samples;
            for (size_t j = 0; j < src.size(); j++) {
                output.samples[j] += src[j] * vol;
            }
        }

        // Clamp to [-1, 1]
        for (auto& s : output.samples) {
            if (s > 1.0f) s = 1.0f;
            if (s < -1.0f) s = -1.0f;
        }
    }
};

AudioMixer::AudioMixer() : impl_(std::make_unique<Impl>()) {}
AudioMixer::~AudioMixer() = default;
void AudioMixer::mix(std::vector<AudioFrame>& sources, AudioFrame& output) {
    impl_->mix(sources, output);
}
bool AudioMixer::init(int sample_rate, int channels) {
    impl_->sample_rate_ = sample_rate;
    impl_->channels_ = channels;
    return true;
}
void AudioMixer::set_volume(int source_index, float volume) {
    if (source_index >= static_cast<int>(impl_->volumes_.size())) {
        impl_->volumes_.resize(source_index + 1, 1.0f);
    }
    impl_->volumes_[source_index] = volume;
}
void AudioMixer::shutdown() {}
