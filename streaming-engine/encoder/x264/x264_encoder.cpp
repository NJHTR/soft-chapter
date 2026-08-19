#include "encoder/x264/x264_encoder.h"
#include <iostream>
#include <cstring>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavutil/opt.h>
#include <libavutil/imgutils.h>
}

class X264Encoder::Impl {
public:
    EncoderConfig config_;
    EncodedCallback callback_;

    const AVCodec* codec_ = nullptr;
    AVCodecContext* ctx_ = nullptr;
    AVFrame* frame_ = nullptr;
    AVPacket* packet_ = nullptr;
    int64_t pts_ = 0;

    bool init(const EncoderConfig& config) {
        config_ = config;

        // Find H.264 encoder
        codec_ = avcodec_find_encoder_by_name("libx264");
        if (!codec_) {
            codec_ = avcodec_find_encoder(AV_CODEC_ID_H264);
        }
        if (!codec_) {
            std::cerr << "x264 encoder not found" << std::endl;
            return false;
        }

        ctx_ = avcodec_alloc_context3(codec_);
        if (!ctx_) return false;

        ctx_->width = config_.width;
        ctx_->height = config_.height;
        ctx_->time_base = AVRational{1, config_.fps};
        ctx_->framerate = AVRational{config_.fps, 1};
        ctx_->pix_fmt = AV_PIX_FMT_YUV420P;
        ctx_->bit_rate = config_.bitrate;
        ctx_->rc_min_rate = config_.min_bitrate;
        ctx_->rc_max_rate = config_.max_bitrate;
        ctx_->gop_size = config_.gop_size;
        ctx_->max_b_frames = config_.b_frames;
        ctx_->thread_count = 4;

        // x264-specific options
        av_opt_set(ctx_->priv_data, "preset", config_.x264_preset.c_str(), 0);
        av_opt_set(ctx_->priv_data, "tune", config_.x264_tune.c_str(), 0);
        av_opt_set(ctx_->priv_data, "profile", "high", 0);
        av_opt_set(ctx_->priv_data, "crf", std::to_string(config_.cq_level).c_str(), 0);

        int ret = avcodec_open2(ctx_, codec_, nullptr);
        if (ret < 0) {
            std::cerr << "x264: could not open codec" << std::endl;
            return false;
        }

        // Allocate frame and packet
        frame_ = av_frame_alloc();
        frame_->format = AV_PIX_FMT_YUV420P;
        frame_->width = config_.width;
        frame_->height = config_.height;
        av_frame_get_buffer(frame_, 32);

        packet_ = av_packet_alloc();

        std::cout << "x264 encoder initialized: "
                  << config_.width << "x" << config_.height
                  << " @ " << config_.fps << "fps"
                  << " " << config_.bitrate / 1000 << "kbps" << std::endl;
        return true;
    }

    bool encode_frame(std::shared_ptr<Frame> frame,
                      std::vector<uint8_t>& packet_data) {
        if (!ctx_ || !frame) return false;

        // Convert RGBA input to YUV420P
        auto& buf = frame->buffer();
        if (buf.format == PixelFormat::RGBA) {
            // Simple RGB to YUV conversion
            const uint8_t* rgba = buf.data;
            uint8_t* y = frame_->data[0];
            uint8_t* u = frame_->data[1];
            uint8_t* v = frame_->data[2];

            for (int h = 0; h < config_.height; h++) {
                for (int w = 0; w < config_.width; w++) {
                    int offset = h * buf.stride[0] + w * 4;
                    int R = rgba[offset];
                    int G = rgba[offset + 1];
                    int B = rgba[offset + 2];

                    y[h * frame_->linesize[0] + w] =
                        ((66 * R + 129 * G + 25 * B + 128) / 256) + 16;

                    if (h % 2 == 0 && w % 2 == 0) {
                        u[(h/2) * frame_->linesize[1] + (w/2)] =
                            ((-38 * R - 74 * G + 112 * B + 128) / 256) + 128;
                        v[(h/2) * frame_->linesize[2] + (w/2)] =
                            ((112 * R - 94 * G - 18 * B + 128) / 256) + 128;
                    }
                }
            }
        }

        frame_->pts = pts_++;

        int ret = avcodec_send_frame(ctx_, frame_);
        if (ret < 0) return false;

        ret = avcodec_receive_packet(ctx_, packet_);
        if (ret == 0) {
            packet_data.assign(packet_->data, packet_->data + packet_->size);

            if (callback_) {
                callback_(packet_data, frame_->pts * 1000 / config_.fps * 1000,
                          packet_->flags & AV_PKT_FLAG_KEY);
            }

            av_packet_unref(packet_);
            return true;
        }

        return ret == AVERROR(EAGAIN);
    }

    bool flush(std::vector<uint8_t>& packet_data) {
        if (!ctx_) return false;

        int ret = avcodec_send_frame(ctx_, nullptr);
        if (ret < 0) return false;

        ret = avcodec_receive_packet(ctx_, packet_);
        if (ret == 0) {
            packet_data.assign(packet_->data, packet_->data + packet_->size);
            av_packet_unref(packet_);
            return true;
        }

        return false;
    }

    void shutdown() {
        if (packet_) av_packet_free(&packet_);
        if (frame_) av_frame_free(&frame_);
        if (ctx_) avcodec_free_context(&ctx_);
    }
};

X264Encoder::X264Encoder() : impl_(std::make_unique<Impl>()) {}
X264Encoder::~X264Encoder() = default;
bool X264Encoder::init(const EncoderConfig& config) { return impl_->init(config); }
bool X264Encoder::encode(std::shared_ptr<Frame> frame, std::vector<uint8_t>& packet) {
    return impl_->encode_frame(frame, packet);
}
bool X264Encoder::flush(std::vector<uint8_t>& packet) { return impl_->flush(packet); }
void X264Encoder::shutdown() { impl_->shutdown(); }
void X264Encoder::set_bitrate(int bitrate) { impl_->config_.bitrate = bitrate; }
void X264Encoder::request_keyframe() {}
void X264Encoder::set_output_callback(EncodedCallback cb) { impl_->callback_ = std::move(cb); }

// ===== X265Encoder =====

class X265Encoder::Impl {
public:
    EncoderConfig config_;
    EncodedCallback callback_;
    const AVCodec* codec_ = nullptr;
    AVCodecContext* ctx_ = nullptr;
    AVFrame* frame_ = nullptr;
    AVPacket* packet_ = nullptr;
    int64_t pts_ = 0;

    bool init(const EncoderConfig& config) {
        config_ = config;
        codec_ = avcodec_find_encoder_by_name("libx265");
        if (!codec_) {
            codec_ = avcodec_find_encoder(AV_CODEC_ID_H265);
        }
        if (!codec_) {
            std::cerr << "x265 encoder not found" << std::endl;
            return false;
        }

        ctx_ = avcodec_alloc_context3(codec_);
        ctx_->width = config_.width;
        ctx_->height = config_.height;
        ctx_->time_base = AVRational{1, config_.fps};
        ctx_->framerate = AVRational{config_.fps, 1};
        ctx_->pix_fmt = AV_PIX_FMT_YUV420P;
        ctx_->bit_rate = config_.bitrate;
        ctx_->gop_size = config_.gop_size;
        ctx_->max_b_frames = config_.b_frames;
        ctx_->thread_count = 4;

        av_opt_set(ctx_->priv_data, "preset", "medium", 0);
        av_opt_set(ctx_->priv_data, "x265-params", "lossless=0", 0);

        avcodec_open2(ctx_, codec_, nullptr);

        frame_ = av_frame_alloc();
        frame_->format = AV_PIX_FMT_YUV420P;
        frame_->width = config_.width;
        frame_->height = config_.height;
        av_frame_get_buffer(frame_, 32);
        packet_ = av_packet_alloc();

        std::cout << "x265 encoder initialized" << std::endl;
        return true;
    }

    bool encode_frame(std::shared_ptr<Frame> frame,
                      std::vector<uint8_t>& packet_data) {
        if (!ctx_ || !frame) return false;
        frame_->pts = pts_++;
        int ret = avcodec_send_frame(ctx_, frame_);
        if (ret < 0) return false;
        ret = avcodec_receive_packet(ctx_, packet_);
        if (ret == 0) {
            packet_data.assign(packet_->data, packet_->data + packet_->size);
            if (callback_) {
                callback_(packet_data, frame_->pts * 1000 / config_.fps * 1000,
                          packet_->flags & AV_PKT_FLAG_KEY);
            }
            av_packet_unref(packet_);
            return true;
        }
        return ret == AVERROR(EAGAIN);
    }

    void shutdown() {
        if (packet_) av_packet_free(&packet_);
        if (frame_) av_frame_free(&frame_);
        if (ctx_) avcodec_free_context(&ctx_);
    }
};

X265Encoder::X265Encoder() : impl_(std::make_unique<Impl>()) {}
X265Encoder::~X265Encoder() = default;
bool X265Encoder::init(const EncoderConfig& config) { return impl_->init(config); }
bool X265Encoder::encode(std::shared_ptr<Frame> frame, std::vector<uint8_t>& packet) {
    return impl_->encode_frame(frame, packet);
}
bool X265Encoder::flush(std::vector<uint8_t>& packet) { return false; }
void X265Encoder::shutdown() { impl_->shutdown(); }
void X265Encoder::set_bitrate(int bitrate) { impl_->config_.bitrate = bitrate; }
void X265Encoder::request_keyframe() {}
void X265Encoder::set_output_callback(EncodedCallback cb) { impl_->callback_ = std::move(cb); }
