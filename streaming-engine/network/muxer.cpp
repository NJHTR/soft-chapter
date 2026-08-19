#include "network/muxer.h"
#include <iostream>
#include <cstring>
#include <sstream>

// ==================== Pure FLV Muxer (zero FFmpeg dep) ====================
class FLVMuxer : public Muxer {
public:
    FLVMuxer() = default;
    ~FLVMuxer() override { shutdown(); }

    bool init(const MuxerConfig& config) override {
        config_ = config;
        initialized_ = true;
        std::cout << "[Muxer] FLV muxer initialized for: "
                  << config.output_url << std::endl;
        return true;
    }

    bool write_header() override {
        // FLV header: 9 bytes
        // Signature "FLV", version 1, flags (video+audio), header size 9
        std::vector<uint8_t> header;
        header.push_back('F');
        header.push_back('L');
        header.push_back('V');
        header.push_back(1);             // version
        header.push_back(5);             // flags: audio(4) + video(1)
        // header size
        header.push_back(0);
        header.push_back(0);
        header.push_back(0);
        header.push_back(9);

        // PreviousTagSize0: always 0
        header.push_back(0);
        header.push_back(0);
        header.push_back(0);
        header.push_back(0);

        if (callback_) {
            callback_(std::move(header), 0, true);
        }

        // Write metadata on next keyframe
        metadata_written_ = false;
        return true;
    }

    bool write_packet(const EncodedPacket& packet) override {
        if (!initialized_) return false;

        // Write metadata before first keyframe
        if (packet.is_video && packet.is_keyframe && !metadata_written_) {
            write_metadata();
            metadata_written_ = true;
        }

        // FLV tag header: 11 bytes
        // TagType(1) + DataSize(3) + Timestamp(4) + StreamID(3)
        uint8_t tag_type = packet.is_video ? 9 : 8;
        uint32_t data_size = static_cast<uint32_t>(packet.data.size());

        // Convert microseconds to milliseconds for FLV
        int32_t timestamp_ms = static_cast<int32_t>(packet.dts / 1000);

        std::vector<uint8_t> tag;
        tag.push_back(tag_type);
        // DataSize (3 bytes big-endian)
        tag.push_back((data_size >> 16) & 0xFF);
        tag.push_back((data_size >> 8) & 0xFF);
        tag.push_back(data_size & 0xFF);
        // Timestamp (4 bytes, extended)
        tag.push_back(timestamp_ms & 0xFF);
        tag.push_back((timestamp_ms >> 8) & 0xFF);
        tag.push_back((timestamp_ms >> 16) & 0xFF);
        tag.push_back((timestamp_ms >> 24) & 0xFF);
        // StreamID (3 bytes, always 0)
        tag.push_back(0);
        tag.push_back(0);
        tag.push_back(0);

        // Tag data
        tag.insert(tag.end(), packet.data.begin(), packet.data.end());

        // PreviousTagSize (4 bytes)
        uint32_t tag_size = 11 + data_size;
        tag.push_back((tag_size >> 24) & 0xFF);
        tag.push_back((tag_size >> 16) & 0xFF);
        tag.push_back((tag_size >> 8) & 0xFF);
        tag.push_back(tag_size & 0xFF);

        if (callback_) {
            callback_(std::move(tag), packet.dts, packet.is_keyframe);
        }

        return true;
    }

    bool write_trailer() override {
        if (callback_) {
            std::vector<uint8_t> trailer;
            // Empty FLV end tag
            trailer.push_back(0);
            trailer.push_back(0);
            trailer.push_back(0);
            trailer.push_back(0);
            callback_(std::move(trailer), 0, false);
        }
        return true;
    }

    void shutdown() override {
        initialized_ = false;
    }

    bool is_initialized() const override { return initialized_; }

    void set_packet_callback(PacketCallback cb) override {
        callback_ = std::move(cb);
    }

private:
    void write_metadata() {
        // Minimal onMetaData script tag
        // Using AMF0 encoding
        std::vector<uint8_t> metadata;
        metadata.push_back(18); // AMF0: script data type (18) for onMetaData

        std::string name = "onMetaData";
        metadata.push_back(2);  // String type
        uint16_t name_len = static_cast<uint16_t>(name.size());
        metadata.push_back((name_len >> 8) & 0xFF);
        metadata.push_back(name_len & 0xFF);
        metadata.insert(metadata.end(), name.begin(), name.end());

        // ECMA Array
        metadata.push_back(8);  // ECMA array type
        uint32_t count = 5;
        metadata.push_back((count >> 24) & 0xFF);
        metadata.push_back((count >> 16) & 0xFF);
        metadata.push_back((count >> 8) & 0xFF);
        metadata.push_back(count & 0xFF);

        // duration
        write_amf_property(metadata, "duration", 0.0);
        // width
        write_amf_property(metadata, "width", 1920.0);
        // height
        write_amf_property(metadata, "height", 1080.0);
        // videocodecid
        write_amf_property(metadata, "videocodecid", 7.0); // AV1
        // audiocodecid
        write_amf_property(metadata, "audiocodecid", 10.0); // AAC

        // Object end marker
        metadata.push_back(0);
        metadata.push_back(0);
        metadata.push_back(9);  // ObjectEnd marker

        // Wrap in script tag
        std::vector<uint8_t> tag;
        tag.push_back(18); // Tag type: script
        uint32_t data_size = static_cast<uint32_t>(metadata.size());
        tag.push_back((data_size >> 16) & 0xFF);
        tag.push_back((data_size >> 8) & 0xFF);
        tag.push_back(data_size & 0xFF);
        // Timestamp 0
        tag.push_back(0);
        tag.push_back(0);
        tag.push_back(0);
        tag.push_back(0);
        // StreamID 0
        tag.push_back(0);
        tag.push_back(0);
        tag.push_back(0);
        tag.insert(tag.end(), metadata.begin(), metadata.end());

        uint32_t tag_size = 11 + data_size;
        tag.push_back((tag_size >> 24) & 0xFF);
        tag.push_back((tag_size >> 16) & 0xFF);
        tag.push_back((tag_size >> 8) & 0xFF);
        tag.push_back(tag_size & 0xFF);

        if (callback_) {
            callback_(std::move(tag), 0, true);
        }
    }

    void write_amf_property(std::vector<uint8_t>& buf,
                            const std::string& key, double value) {
        uint16_t key_len = static_cast<uint16_t>(key.size());
        buf.push_back(0);
        buf.push_back(0);  // String type for key (AMF0 uses 0x00 0x00 for property name)
        buf.push_back((key_len >> 8) & 0xFF);
        buf.push_back(key_len & 0xFF);
        buf.insert(buf.end(), key.begin(), key.end());

        // Number type (0x00)
        buf.push_back(0);
        // Double value (big-endian)
        uint64_t val;
        memcpy(&val, &value, 8);
        // Swap to big-endian
        for (int i = 7; i >= 0; i--) {
            buf.push_back((val >> (i * 8)) & 0xFF);
        }
    }

    MuxerConfig config_;
    bool initialized_ = false;
    bool metadata_written_ = false;
    PacketCallback callback_;
};

// ==================== FFmpeg Muxer ====================
class FFmpegMuxer : public Muxer {
public:
    FFmpegMuxer() = default;
    ~FFmpegMuxer() override { shutdown(); }

    bool init(const MuxerConfig& config) override {
        config_ = config;

#ifdef ENABLE_FFMPEG
        // Determine format
        const char* fmt = nullptr;
        switch (config.format) {
            case ContainerFormat::FLV:    fmt = "flv";  break;
            case ContainerFormat::MP4:    fmt = "mp4";  break;
            case ContainerFormat::MPEGTS: fmt = "mpegts"; break;
            default: fmt = "flv"; break;
        }

        // Allocate output context
        int ret = avformat_alloc_output_context2(&fmt_ctx_, nullptr,
                                                  fmt, nullptr);
        if (ret < 0 || !fmt_ctx_) {
            std::cerr << "[Muxer] Failed to alloc output context" << std::endl;
            return false;
        }

        // Create IO context
        io_buffer_ = static_cast<uint8_t*>(av_malloc(io_buf_size_));
        io_ctx_ = avio_alloc_context(io_buffer_, io_buf_size_,
                                      AVIO_FLAG_WRITE,
                                      this, nullptr, &write_packet_static,
                                      &seek_static);
        fmt_ctx_->pb = io_ctx_;

        initialized_ = true;
        return true;
#else
        std::cerr << "[Muxer] FFmpeg not enabled" << std::endl;
        return false;
#endif
    }

    bool write_header() override {
#ifdef ENABLE_FFMPEG
        if (!fmt_ctx_) return false;

        int ret = avformat_write_header(fmt_ctx_, nullptr);
        if (ret < 0) {
            std::cerr << "[Muxer] Failed to write header" << std::endl;
            return false;
        }
        header_written_ = true;
#endif
        return true;
    }

    bool write_packet(const EncodedPacket& packet) override {
#ifdef ENABLE_FFMPEG
        if (!fmt_ctx_) return false;

        AVPacket* pkt = av_packet_alloc();
        if (!pkt) return false;

        pkt->data = const_cast<uint8_t*>(packet.data.data());
        pkt->size = static_cast<int>(packet.data.size());
        pkt->pts = packet.pts * config_.video_timescale / 1000000;
        pkt->dts = packet.dts * config_.video_timescale / 1000000;
        pkt->flags = packet.is_keyframe ? AV_PKT_FLAG_KEY : 0;
        pkt->stream_index = packet.is_video ? 0 : 1;

        int ret = av_interleaved_write_frame(fmt_ctx_, pkt);
        av_packet_free(&pkt);

        if (ret < 0) {
            std::cerr << "[Muxer] Failed to write packet: " << ret << std::endl;
            return false;
        }
#endif
        return true;
    }

    bool write_trailer() override {
#ifdef ENABLE_FFMPEG
        if (fmt_ctx_ && header_written_) {
            av_write_trailer(fmt_ctx_);
        }
#endif
        return true;
    }

    void shutdown() override {
#ifdef ENABLE_FFMPEG
        if (fmt_ctx_) {
            if (io_ctx_) {
                avio_closep(&io_ctx_);
            } else {
                avformat_free_context(fmt_ctx_);
            }
            fmt_ctx_ = nullptr;
        }
#endif
        initialized_ = false;
    }

    bool is_initialized() const override { return initialized_; }

    void set_packet_callback(PacketCallback cb) override {
        callback_ = std::move(cb);
    }

private:
    static int write_packet_static(void* opaque, uint8_t* buf, int buf_size) {
        auto* self = static_cast<FFmpegMuxer*>(opaque);
        if (self->callback_) {
            std::vector<uint8_t> data(buf, buf + buf_size);
            self->callback_(std::move(data), 0, false);
        }
        return buf_size;
    }

    static int64_t seek_static(void* opaque, int64_t offset, int whence) {
        return 0;  // No seeking in live stream
    }

    MuxerConfig config_;
    bool initialized_ = false;
    bool header_written_ = false;
    PacketCallback callback_;

#ifdef ENABLE_FFMPEG
    AVFormatContext* fmt_ctx_ = nullptr;
    AVIOContext* io_ctx_ = nullptr;
    uint8_t* io_buffer_ = nullptr;
    int io_buf_size_ = 32768;
#endif
};

// ==================== Factory ====================
std::unique_ptr<Muxer> create_muxer(const MuxerConfig& config) {
    auto muxer = std::make_unique<FFmpegMuxer>();
    if (muxer->init(config)) {
        return muxer;
    }
    return nullptr;
}

std::unique_ptr<Muxer> create_flv_muxer() {
    return std::make_unique<FLVMuxer>();
}
