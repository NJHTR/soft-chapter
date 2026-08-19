#include "network/srt/srt_streamer.h"
#include <iostream>
#include <thread>
#include <atomic>
#include <chrono>

#ifdef ENABLE_SRT
#include <srt/srt.h>
#include <udt.h>
#endif

class SRTStreamer::Impl {
public:
    StreamConfig config_;
    std::atomic<bool> connected_{false};

#ifdef ENABLE_SRT
    SRTSOCKET srt_socket_ = SRT_INVALID_SOCK;
    std::thread stats_thread_;

    // Statistics
    std::atomic<int64_t> bytes_sent_{0};
    std::atomic<int64_t> bytes_retransmitted_{0};
    std::atomic<double> current_rtt_{0.0};
    std::atomic<double> current_loss_{0.0};
    std::atomic<double> current_bandwidth_{0.0};
#endif

    bool init(const StreamConfig& config) {
        config_ = config;

#ifdef ENABLE_SRT
        // Initialize SRT library
        srt_startup();

        std::cout << "SRT streamer configured: "
                  << config_.server_url << ":" << config_.server_port
                  << " latency=" << config_.srt_latency_ms << "ms"
                  << std::endl;
        return true;
#else
        std::cerr << "SRT not enabled in build" << std::endl;
        return false;
#endif
    }

    bool connect() {
#ifdef ENABLE_SRT
        srt_socket_ = srt_create_socket();
        if (srt_socket_ == SRT_INVALID_SOCK) {
            std::cerr << "SRT: failed to create socket" << std::endl;
            return false;
        }

        // Configure SRT options
        int64_t latency = config_.srt_latency_ms;
        srt_setsockopt(srt_socket_, 0, SRTO_LATENCY, &latency, sizeof(latency));

        int64_t tsbpd = config_.srt_tsbpd_ms;
        srt_setsockopt(srt_socket_, 0, SRTO_TSBPDMODE, &tsbpd, sizeof(tsbpd));

        int64_t maxbw = 1000000000; // 1 Gbps max
        srt_setsockopt(srt_socket_, 0, SRTO_MAXBW, &maxbw, sizeof(maxbw));

        int64_t sendbuf = 8192 * 1500;
        srt_setsockopt(srt_socket_, 0, SRTO_SNDBUF, &sendbuf, sizeof(sendbuf));

        int64_t recvbuf = 8192 * 1500;
        srt_setsockopt(srt_socket_, 0, SRTO_RCVBUF, &recvbuf, sizeof(recvbuf));

        // Enable encryption if configured
        if (config_.srt_encryption && !config_.srt_passphrase.empty()) {
            int32_t pbkeylen = 16; // AES-128
            srt_setsockopt(srt_socket_, 0, SRTO_PBKEYLEN, &pbkeylen, sizeof(pbkeylen));
            srt_setsockopt(srt_socket_, 0, SRTO_PASSPHRASE,
                           config_.srt_passphrase.data(),
                           (int)config_.srt_passphrase.size());
        }

        // Build SRT URL
        std::string url = config_.server_url;
        if (url.empty()) {
            url = "srt://localhost:9000?streamid=live/" + config_.stream_key;
        }

        sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_port = htons(config_.server_port > 0 ? config_.server_port : 9000);
        addr.sin_addr.s_addr = inet_addr("127.0.0.1");

        // Parse URL for host
        // In production: proper URL parsing

        int ret = srt_connect(srt_socket_, (sockaddr*)&addr, sizeof(addr));
        if (ret == SRT_ERROR) {
            std::cerr << "SRT: connection failed: " << srt_getlasterror_str() << std::endl;
            srt_close(srt_socket_);
            srt_socket_ = SRT_INVALID_SOCK;
            return false;
        }

        connected_ = true;

        // Start stats monitoring thread
        stats_thread_ = std::thread([this]() {
            while (connected_) {
                SRT_TRACEBSTATS stats;
                if (srt_bistats(srt_socket_, &stats, 0, 0) != SRT_ERROR) {
                    current_rtt_ = static_cast<double>(stats.msRTT);
                    current_loss_ = static_cast<double>(stats.pktSndLossTotal) /
                                    std::max(1.0, static_cast<double>(stats.pktSentTotal));
                    current_bandwidth_ = static_cast<double>(stats.mbpsBandwidth);
                    bytes_sent_ = static_cast<int64_t>(stats.byteSentTotal);
                    bytes_retransmitted_ = static_cast<int64_t>(stats.byteSentUniqTotal);
                }
                std::this_thread::sleep_for(std::chrono::seconds(2));
            }
        });

        std::cout << "SRT connected to " << url << std::endl;
        return true;
#else
        return false;
#endif
    }

    bool disconnect() {
        connected_ = false;

#ifdef ENABLE_SRT
        if (stats_thread_.joinable()) stats_thread_.join();
        if (srt_socket_ != SRT_INVALID_SOCK) {
            srt_close(srt_socket_);
            srt_socket_ = SRT_INVALID_SOCK;
        }
        srt_cleanup();
#endif

        std::cout << "SRT disconnected" << std::endl;
        return true;
    }

    bool send_packet(const uint8_t* data, size_t size,
                     int64_t timestamp, bool is_keyframe) {
        if (!connected_) return false;

#ifdef ENABLE_SRT
        // Add SRT data header with timestamp
        // SRT uses its own timing, just send raw TS packets

        int ret = srt_sendmsg2(srt_socket_, (const char*)data, (int)size, nullptr);
        if (ret == SRT_ERROR) {
            if (srt_getlasterror(nullptr) == SRT_EASYNCSND) {
                // Would block - drop frame (TikTok behavior: drop non-keyframes)
                if (!is_keyframe) {
                    return true; // Silently drop
                }
                // For keyframes, wait
                ret = srt_sendmsg2(srt_socket_, (const char*)data, (int)size, nullptr);
            }
            if (ret == SRT_ERROR) {
                std::cerr << "SRT send error: " << srt_getlasterror_str() << std::endl;
                return false;
            }
        }

        return true;
#else
        return false;
#endif
    }

    bool is_connected() const { return connected_; }
    double rtt_ms() const { return current_rtt_; }
    double packet_loss() const { return current_loss_; }
    int64_t bytes_sent() const { return bytes_sent_; }
    int64_t bytes_retransmitted() const { return bytes_retransmitted_; }
    double bandwidth_mbps() const { return current_bandwidth_; }

    ~Impl() {
        disconnect();
    }
};

SRTStreamer::SRTStreamer() : impl_(std::make_unique<Impl>()) {}
SRTStreamer::~SRTStreamer() = default;

bool SRTStreamer::init(const StreamConfig& config) { return impl_->init(config); }
bool SRTStreamer::connect() { return impl_->connect(); }
bool SRTStreamer::disconnect() { return impl_->disconnect(); }
bool SRTStreamer::send_packet(const uint8_t* data, size_t size,
                               int64_t ts, bool key) {
    return impl_->send_packet(data, size, ts, key);
}
bool SRTStreamer::is_connected() const { return impl_->is_connected(); }
double SRTStreamer::rtt_ms() const { return impl_->rtt_ms(); }
double SRTStreamer::packet_loss() const { return impl_->packet_loss(); }
int64_t SRTStreamer::bytes_sent() const { return impl_->bytes_sent(); }
int64_t SRTStreamer::bytes_retransmitted() const { return impl_->bytes_retransmitted(); }
double SRTStreamer::bandwidth_mbps() const { return impl_->bandwidth_mbps(); }
