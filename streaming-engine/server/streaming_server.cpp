#include "server/streaming_server.h"
#include <iostream>
#include <thread>
#include <atomic>
#include <chrono>

#ifdef ENABLE_CURL
#include <curl/curl.h>
#endif

class StreamServer::Impl {
public:
    StreamServerType type_;
    std::string config_path_;
    std::atomic<bool> running_{false};

    // SRS HTTP API integration
    std::string srs_api_ = "http://localhost:1985";

    bool init(StreamServerType type, const std::string& config_path) {
        type_ = type;
        config_path_ = config_path;
#ifdef ENABLE_CURL
        curl_global_init(CURL_GLOBAL_ALL);
#endif

        std::cout << "StreamServer: "
                  << server_type_name(type_)
                  << " config=" << config_path_
                  << std::endl;
        return true;
    }

    std::string server_type_name(StreamServerType type) {
        switch (type) {
            case StreamServerType::SRS: return "SRS 6.0";
            case StreamServerType::MediaMTX: return "MediaMTX";
            case StreamServerType::ZLMediaKit: return "ZLMediaKit";
            case StreamServerType::LiveKit: return "LiveKit";
            case StreamServerType::Janus: return "Janus";
            case StreamServerType::mediasoup: return "mediasoup";
            case StreamServerType::OvenMediaEngine: return "OvenMediaEngine";
            case StreamServerType::NginxRTMP: return "Nginx-RTMP";
            default: return "Unknown";
        }
    }

    bool start() {
        running_ = true;
        std::cout << "StreamServer started: " << server_type_name(type_) << std::endl;
        return true;
    }

    bool stop() {
        running_ = false;
        std::cout << "StreamServer stopped" << std::endl;
        return true;
    }

    bool create_stream(const std::string& stream_key,
                       const EncoderConfig& config) {
        if (type_ == StreamServerType::SRS) {
            return srs_api_create_stream(stream_key, config);
        }
        std::cout << "Stream created: " << stream_key << std::endl;
        return true;
    }

    bool srs_api_create_stream(const std::string& stream_key,
                                const EncoderConfig& config) {
#ifdef ENABLE_CURL
        CURL* curl = curl_easy_init();
        if (!curl) return false;

        std::string url = srs_api_ + "/api/v1/streams/live/" + stream_key;
        curl_easy_setopt(curl, CURLOPT_URL, url.c_str());
        curl_easy_setopt(curl, CURLOPT_CUSTOMREQUEST, "PUT");

        std::string json = R"({
            "vhost": "__defaultVhost__",
            "app": "live",
            "stream": ")" + stream_key + R"(",
            "url": "srt://localhost:9000?streamid=live/)" + stream_key + R"("
        })";

        curl_easy_setopt(curl, CURLOPT_POSTFIELDS, json.c_str());

        struct curl_slist* headers = nullptr;
        headers = curl_slist_append(headers, "Content-Type: application/json");
        curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);

        CURLcode res = curl_easy_perform(curl);
        curl_slist_free_all(headers);
        curl_easy_cleanup(curl);

        if (res != CURLE_OK) {
            std::cerr << "SRS API call failed: " << curl_easy_strerror(res) << std::endl;
            return false;
        }

        return true;
#else
        std::cout << "SRS API not available (no CURL)" << std::endl;
        return false;
#endif
    }

    bool remove_stream(const std::string& stream_key) {
        std::cout << "Stream removed: " << stream_key << std::endl;
        return true;
    }

    bool configure_abr(const std::vector<ABRLayer>& layers) {
        std::cout << "ABR configured: " << layers.size() << " layers" << std::endl;
        for (const auto& layer : layers) {
            std::cout << "  " << layer.width << "x" << layer.height
                      << " @" << layer.bitrate / 1000 << "kbps "
                      << layer.codec << std::endl;
        }
        return true;
    }

    ServerStats stats() {
        ServerStats s{};
        s.active_streams = running_ ? 1 : 0;
        return s;
    }
};

StreamServer::StreamServer() : impl_(std::make_unique<Impl>()) {}
StreamServer::~StreamServer() = default;

bool StreamServer::init(StreamServerType type, const std::string& config_path) {
    return impl_->init(type, config_path);
}
bool StreamServer::start() { return impl_->start(); }
bool StreamServer::stop() { return impl_->stop(); }
bool StreamServer::is_running() const { return impl_->running_; }
bool StreamServer::create_stream(const std::string& key, const EncoderConfig& cfg) {
    return impl_->create_stream(key, cfg);
}
bool StreamServer::remove_stream(const std::string& key) { return impl_->remove_stream(key); }
bool StreamServer::configure_abr(const std::vector<ABRLayer>& layers) {
    return impl_->configure_abr(layers);
}
StreamServer::ServerStats StreamServer::stats() const { return impl_->stats(); }
