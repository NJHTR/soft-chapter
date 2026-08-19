#include "capture/ffmpeg/ffmpeg_capture.h"
#include <iostream>
#include <thread>
#include <atomic>

extern "C" {
#include <libavcodec/avcodec.h>
#include <libavformat/avformat.h>
#include <libavdevice/avdevice.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>
}

class FFmpegCapture::Impl {
public:
    CaptureConfig config_;
    std::atomic<bool> running_{false};
    FrameCallback frame_cb_;
    std::thread capture_thread_;

    AVFormatContext* fmt_ctx_ = nullptr;
    AVCodecContext* codec_ctx_ = nullptr;
    const AVInputFormat* input_fmt_ = nullptr;
    int video_stream_idx_ = -1;
    SwsContext* sws_ctx_ = nullptr;

    bool init(const CaptureConfig& config) {
        config_ = config;

        // Register all FFmpeg devices
        avdevice_register_all();
        avformat_network_init();

        // Open input
        AVDictionary* options = nullptr;
        av_dict_set(&options, "framerate", std::to_string(config_.fps).c_str(), 0);
        av_dict_set(&options, "video_size",
                    (std::to_string(config_.width) + "x" + std::to_string(config_.height)).c_str(), 0);

        std::string input_url = config_.ffmpeg_input_url;
        if (input_url.empty()) {
            // Default camera device per platform
#ifdef _WIN32
            input_url = "dshow";
            input_fmt_ = av_find_input_format("dshow");
            av_dict_set(&options, "video_size",
                        (std::to_string(config_.width) + "x" + std::to_string(config_.height)).c_str(), 0);
#elif __APPLE__
            input_url = "0";  // Default AVFoundation device
            input_fmt_ = av_find_input_format("avfoundation");
#elif __linux__
            input_url = "/dev/video0";
            input_fmt_ = av_find_input_format("v4l2");
#endif
        }

        int ret = avformat_open_input(&fmt_ctx_, input_url.c_str(), input_fmt_, &options);
        if (ret < 0) {
            char errbuf[256];
            av_strerror(ret, errbuf, sizeof(errbuf));
            std::cerr << "FFmpegCapture: could not open input: " << errbuf << std::endl;
            return false;
        }

        ret = avformat_find_stream_info(fmt_ctx_, nullptr);
        if (ret < 0) {
            std::cerr << "FFmpegCapture: could not find stream info" << std::endl;
            return false;
        }

        // Find video stream
        for (unsigned i = 0; i < fmt_ctx_->nb_streams; i++) {
            if (fmt_ctx_->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
                video_stream_idx_ = i;
                break;
            }
        }

        if (video_stream_idx_ < 0) {
            std::cerr << "FFmpegCapture: no video stream found" << std::endl;
            return false;
        }

        // Open decoder
        const AVCodec* codec = avcodec_find_decoder(
            fmt_ctx_->streams[video_stream_idx_]->codecpar->codec_id);
        if (!codec) {
            std::cerr << "FFmpegCapture: no decoder found" << std::endl;
            return false;
        }

        codec_ctx_ = avcodec_alloc_context3(codec);
        avcodec_parameters_to_context(codec_ctx_,
            fmt_ctx_->streams[video_stream_idx_]->codecpar);
        avcodec_open2(codec_ctx_, codec, nullptr);

        std::cout << "FFmpegCapture opened: "
                  << codec_ctx_->width << "x" << codec_ctx_->height
                  << " @ " << config_.fps << "fps" << std::endl;
        return true;
    }

    bool start() {
        if (running_) return true;
        running_ = true;

        capture_thread_ = std::thread([this]() {
            AVPacket* packet = av_packet_alloc();
            AVFrame* frame = av_frame_alloc();
            AVFrame* rgb_frame = av_frame_alloc();

            int rgb_size = av_image_get_buffer_size(AV_PIX_FMT_RGBA,
                codec_ctx_->width, codec_ctx_->height, 1);
            uint8_t* rgb_buffer = (uint8_t*)av_malloc(rgb_size);
            av_image_fill_arrays(rgb_frame->data, rgb_frame->linesize,
                rgb_buffer, AV_PIX_FMT_RGBA,
                codec_ctx_->width, codec_ctx_->height, 1);

            SwsContext* sws = sws_getContext(
                codec_ctx_->width, codec_ctx_->height, codec_ctx_->pix_fmt,
                codec_ctx_->width, codec_ctx_->height, AV_PIX_FMT_RGBA,
                SWS_BICUBIC, nullptr, nullptr, nullptr);

            while (running_) {
                int ret = av_read_frame(fmt_ctx_, packet);
                if (ret < 0) continue;

                if (packet->stream_index == video_stream_idx_) {
                    ret = avcodec_send_packet(codec_ctx_, packet);
                    if (ret < 0) continue;

                    ret = avcodec_receive_frame(codec_ctx_, frame);
                    if (ret == 0) {
                        // Convert to RGBA
                        sws_scale(sws, frame->data, frame->linesize,
                                  0, codec_ctx_->height,
                                  rgb_frame->data, rgb_frame->linesize);

                        // Create output frame
                        auto out_frame = Frame::create_cpu(
                            codec_ctx_->width, codec_ctx_->height,
                            PixelFormat::RGBA);

                        if (out_frame) {
                            memcpy(out_frame->buffer().data, rgb_buffer, rgb_size);
                            out_frame->buffer().timestamp =
                                std::chrono::duration_cast<std::chrono::milliseconds>(
                                    std::chrono::steady_clock::now().time_since_epoch()).count();

                            if (frame_cb_) frame_cb_(out_frame);
                        }
                    }
                }
                av_packet_unref(packet);
            }

            sws_freeContext(sws);
            av_free(rgb_buffer);
            av_frame_free(&rgb_frame);
            av_frame_free(&frame);
            av_packet_free(&packet);
        });

        return true;
    }

    bool stop() {
        running_ = false;
        if (capture_thread_.joinable()) capture_thread_.join();
        return true;
    }

    ~Impl() {
        if (codec_ctx_) avcodec_free_context(&codec_ctx_);
        if (fmt_ctx_) avformat_close_input(&fmt_ctx_);
        avformat_network_deinit();
    }

    std::string device_name() const {
        return "FFmpeg: " + config_.ffmpeg_input_url;
    }
};

FFmpegCapture::FFmpegCapture() : impl_(std::make_unique<Impl>()) {}
FFmpegCapture::~FFmpegCapture() = default;

bool FFmpegCapture::init(const CaptureConfig& config) { return impl_->init(config); }
bool FFmpegCapture::start() { return impl_->start(); }
bool FFmpegCapture::stop() { return impl_->stop(); }
bool FFmpegCapture::read_frame(std::shared_ptr<Frame>& frame) { return false; } // Async
void FFmpegCapture::set_frame_callback(FrameCallback cb) { impl_->frame_cb_ = std::move(cb); }
std::string FFmpegCapture::device_name() const { return impl_->device_name(); }
