#pragma once

#include "common/frame.h"
#include <memory>
#include <vector>
#include <functional>

// Audio capture and encoding pipeline
// Supports: AAC, Opus, MP3

enum class AudioCodec {
    AAC,
    Opus,
    MP3,
    PCM,
};

struct AudioConfig {
    AudioCodec codec = AudioCodec::AAC;
    int sample_rate = 48000;
    int channels = 2;        // stereo
    int bitrate = 128000;    // 128 kbps
    int frame_size = 1024;   // samples per frame
};

struct AudioFrame {
    std::vector<float> samples;    // interleaved float samples [-1, 1]
    int sample_rate;
    int channels;
    int64_t timestamp;             // microseconds
    int frame_index;
};

class AudioCapture {
public:
    virtual ~AudioCapture() = default;
    virtual bool init(int sample_rate, int channels) = 0;
    virtual bool start() = 0;
    virtual bool stop() = 0;
    virtual bool read_frame(AudioFrame& frame) = 0;

    using AudioCallback = std::function<void(const AudioFrame&)>;
    virtual void set_callback(AudioCallback cb) = 0;
};

class AudioEncoder {
public:
    virtual ~AudioEncoder() = default;
    virtual bool init(const AudioConfig& config) = 0;
    virtual bool encode(const AudioFrame& input,
                        std::vector<uint8_t>& output) = 0;
    virtual bool flush(std::vector<uint8_t>& output) = 0;
    virtual void shutdown() = 0;

    using EncodedCallback = std::function<void(std::vector<uint8_t> packet,
                                                int64_t timestamp)>;
    virtual void set_output_callback(EncodedCallback cb) = 0;

    virtual AudioCodec codec() const = 0;
};

// Audio mixer: combines multiple audio sources (mic, system, music)
class AudioMixer {
public:
    AudioMixer();
    ~AudioMixer();

    bool init(int sample_rate, int channels);
    void mix(std::vector<AudioFrame>& sources, AudioFrame& output);
    void set_volume(int source_index, float volume);
    void shutdown();

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

std::unique_ptr<AudioCapture> create_audio_capture();
std::unique_ptr<AudioEncoder> create_audio_encoder(const AudioConfig& config);
