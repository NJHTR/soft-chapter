#include "server/transcode/transcoder.h"
#include <iostream>

class Transcoder::Impl {
public:
    TranscodeConfig config_;
    std::vector<std::unique_ptr<Encoder>> encoders_;
    ABRCallback output_cb_;
    bool initialized_ = false;

    bool init(const TranscodeConfig& config) {
        config_ = config;

        // Create one encoder per ABR tier
        for (size_t i = 0; i < config_.tiers.size(); i++) {
            const auto& tier = config_.tiers[i];

            EncoderConfig enc_cfg;
            enc_cfg.width = tier.width;
            enc_cfg.height = tier.height;
            enc_cfg.fps = tier.fps;
            enc_cfg.bitrate = tier.bitrate;
            enc_cfg.codec = tier.codec;

            if (tier.codec == Codec::AV1) {
                enc_cfg.type = EncoderType::NVENC;
            } else {
                enc_cfg.type = EncoderType::x265;
            }

            auto encoder = create_encoder(enc_cfg);
            if (encoder && encoder->init(enc_cfg)) {
                // Wrap callback to include tier index
                int tier_idx = static_cast<int>(i);
                encoder->set_output_callback(
                    [this, tier_idx](std::vector<uint8_t> packet,
                                     int64_t timestamp, bool is_keyframe) {
                        if (output_cb_) {
                            output_cb_(tier_idx, std::move(packet),
                                       timestamp, is_keyframe);
                        }
                    });
                encoders_.push_back(std::move(encoder));
            }
        }

        initialized_ = true;
        std::cout << "Transcoder initialized: "
                  << config_.tiers.size() << " ABR tiers" << std::endl;
        return true;
    }

    bool transcode_frame(std::shared_ptr<Frame> frame) {
        if (!initialized_ || !frame) return false;

        // Encode the same frame at multiple quality levels
        // In production: scale frame per tier using GPU
        for (auto& encoder : encoders_) {
            std::vector<uint8_t> packet;
            encoder->encode(frame, packet);
        }

        return true;
    }

    void shutdown() {
        for (auto& encoder : encoders_) {
            encoder->shutdown();
        }
        encoders_.clear();
        initialized_ = false;
    }
};

Transcoder::Transcoder() : impl_(std::make_unique<Impl>()) {}
Transcoder::~Transcoder() = default;

bool Transcoder::init(const TranscodeConfig& config) { return impl_->init(config); }
bool Transcoder::transcode(std::shared_ptr<Frame> frame) {
    return impl_->transcode_frame(frame);
}
void Transcoder::shutdown() { impl_->shutdown(); }
