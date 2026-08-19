#include "network/rtmp_streamer.h"
#include "network/muxer.h"
#include <iostream>
#include <thread>
#include <chrono>
#include <cstring>
#include <atomic>

#ifdef _WIN32
#define NOMINMAX
#include <winsock2.h>
#include <ws2tcpip.h>
#pragma comment(lib, "ws2_32.lib")
#else
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netdb.h>
#include <unistd.h>
#endif

// Simple RTMP implementation
// RTMP protocol basics:
// 1. Handshake (C0C1C2 / S0S1S2)
// 2. Connect command
// 3. CreateStream
// 4. Publish
// 5. Send audio/video data packets

class RTMPStreamer::Impl {
public:
    RTMPConfig config_;
    std::function<void(const std::string&)> status_callback_;
    std::string last_error_;
    RTMPStats stats_;

    MuxerConfig muxer_config_;
    std::unique_ptr<Muxer> muxer_;

#ifdef _WIN32
    SOCKET sock_ = INVALID_SOCKET;
#else
    int sock_ = -1;
#endif
    bool connected_ = false;
    std::atomic<bool> running_{false};
    int reconnect_count_ = 0;

    std::string host_;
    int port_ = 1935;
    std::string app_;
    std::string stream_key_;

    // Stats
    std::chrono::steady_clock::time_point start_time_;
    int64_t bytes_sent_ = 0;
    int64_t packets_sent_ = 0;

    bool init(const RTMPConfig& config) {
        config_ = config;

        // Parse URL: rtmp://host:port/app/streamkey
        if (!parse_url(config.url)) {
            last_error_ = "Invalid RTMP URL";
            return false;
        }

        // Create FLV muxer
        muxer_config_.format = ContainerFormat::FLV;
        muxer_config_.output_url = config.url;
        muxer_ = create_flv_muxer();
        if (!muxer_) {
            last_error_ = "Failed to create FLV muxer";
            return false;
        }
        muxer_->init(muxer_config_);

        // Setup muxer callback to send FLV data over RTMP
        muxer_->set_packet_callback(
            [this](std::vector<uint8_t> data, int64_t dts, bool is_keyframe) {
                send_raw(data);
            });

        std::cout << "[RTMP] Initialized for: " << config.url << std::endl;
        return true;
    }

    bool parse_url(const std::string& url) {
        // rtmp://host:port/app/streamkey
        std::string remaining;

        // Skip protocol
        size_t pos = url.find("://");
        if (pos == std::string::npos) return false;
        remaining = url.substr(pos + 3);

        // Host:Port
        pos = remaining.find('/');
        if (pos == std::string::npos) return false;
        std::string host_part = remaining.substr(0, pos);
        remaining = remaining.substr(pos + 1);

        // Check for port
        pos = host_part.find(':');
        if (pos != std::string::npos) {
            host_ = host_part.substr(0, pos);
            port_ = std::stoi(host_part.substr(pos + 1));
        } else {
            host_ = host_part;
            port_ = 1935;
        }

        // App/StreamKey
        pos = remaining.find('/');
        if (pos != std::string::npos) {
            app_ = remaining.substr(0, pos);
            stream_key_ = remaining.substr(pos + 1);
        } else {
            app_ = remaining;
            stream_key_ = "";
        }

        return !host_.empty();
    }

    bool connect() {
#ifdef _WIN32
        WSADATA wsa_data;
        if (WSAStartup(MAKEWORD(2, 2), &wsa_data) != 0) {
            last_error_ = "WSAStartup failed";
            return false;
        }
#endif

        struct addrinfo hints = {};
        hints.ai_family = AF_UNSPEC;
        hints.ai_socktype = SOCK_STREAM;
        hints.ai_protocol = IPPROTO_TCP;

        struct addrinfo* result = nullptr;
        std::string port_str = std::to_string(port_);

        int ret = getaddrinfo(host_.c_str(), port_str.c_str(), &hints, &result);
        if (ret != 0) {
#ifdef _WIN32
            WSACleanup();
#endif
            last_error_ = "Failed to resolve hostname: " + host_;
            if (status_callback_) status_callback_("error: " + last_error_);
            return false;
        }

        // Try each address
        for (struct addrinfo* ai = result; ai != nullptr; ai = ai->ai_next) {
#ifdef _WIN32
            sock_ = socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
            if (sock_ == INVALID_SOCKET) continue;
#else
            sock_ = ::socket(ai->ai_family, ai->ai_socktype, ai->ai_protocol);
            if (sock_ < 0) continue;
#endif

            // Set timeout
#ifdef _WIN32
            DWORD timeout = config_.timeout_ms;
            setsockopt(sock_, SOL_SOCKET, SO_RCVTIMEO,
                       (const char*)&timeout, sizeof(timeout));
            setsockopt(sock_, SOL_SOCKET, SO_SNDTIMEO,
                       (const char*)&timeout, sizeof(timeout));
#else
            struct timeval tv;
            tv.tv_sec = config_.timeout_ms / 1000;
            tv.tv_usec = (config_.timeout_ms % 1000) * 1000;
            setsockopt(sock_, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv));
            setsockopt(sock_, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
#endif

            auto start = std::chrono::steady_clock::now();
            if (::connect(sock_, ai->ai_addr, static_cast<int>(ai->ai_addrlen)) == 0) {
                auto end = std::chrono::steady_clock::now();
                stats_.connect_time_ms =
                    std::chrono::duration_cast<std::chrono::milliseconds>(
                        end - start).count();
                break;
            }

#ifdef _WIN32
            closesocket(sock_);
            sock_ = INVALID_SOCKET;
#else
            ::close(sock_);
            sock_ = -1;
#endif
        }

        freeaddrinfo(result);

#ifdef _WIN32
        if (sock_ == INVALID_SOCKET) {
            WSACleanup();
            last_error_ = "Failed to connect";
            if (status_callback_) status_callback_("error: connect failed");
            return false;
        }
#else
        if (sock_ < 0) {
            last_error_ = "Failed to connect";
            if (status_callback_) status_callback_("error: connect failed");
            return false;
        }
#endif

        // Perform RTMP handshake
        if (!rtmp_handshake()) {
            disconnect();
            return false;
        }

        // Send connect command
        if (!send_connect()) {
            disconnect();
            return false;
        }

        // Wait for connect result
        if (!read_server_response()) {
            disconnect();
            return false;
        }

        // CreateStream
        if (!send_create_stream()) {
            disconnect();
            return false;
        }

        // Wait for createStream response
        if (!read_server_response()) {
            disconnect();
            return false;
        }

        // Publish
        if (!send_publish()) {
            disconnect();
            return false;
        }

        // Send FLV header
        muxer_->write_header();

        connected_ = true;
        start_time_ = std::chrono::steady_clock::now();
        stats_.reconnect_count = reconnect_count_;

        std::cout << "[RTMP] Connected to: " << config_.url << std::endl;
        if (status_callback_) status_callback_("connected");

        return true;
    }

    bool disconnect() {
        if (muxer_) {
            muxer_->write_trailer();
        }

#ifdef _WIN32
        if (sock_ != INVALID_SOCKET) {
            closesocket(sock_);
            sock_ = INVALID_SOCKET;
            WSACleanup();
        }
#else
        if (sock_ >= 0) {
            ::close(sock_);
            sock_ = -1;
        }
#endif

        connected_ = false;
        if (status_callback_) status_callback_("disconnected");
        return true;
    }

    bool send_packet(const EncodedPacket& packet) {
        if (!connected_) return false;

        if (muxer_) {
            return muxer_->write_packet(packet);
        }
        return false;
    }

    bool is_connected() const { return connected_; }

    // ==================== RTMP Protocol ====================

    // RTMP handshake: C0 (1 byte) + C1 (1536 bytes) + C2 (1536 bytes)
    bool rtmp_handshake() {
        // Send C0: version = 3
        uint8_t c0 = 3;
        if (send_all(&c0, 1) != 1) {
            last_error_ = "Handshake: failed to send C0";
            return false;
        }

        // Send C1: 1536 bytes (4 bytes time + 4 bytes version + 1528 bytes random)
        std::vector<uint8_t> c1(1536, 0);
        uint32_t time_epoch = static_cast<uint32_t>(
            std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count() & 0xFFFFFFFF);
        c1[0] = (time_epoch >> 24) & 0xFF;
        c1[1] = (time_epoch >> 16) & 0xFF;
        c1[2] = (time_epoch >> 8) & 0xFF;
        c1[3] = time_epoch & 0xFF;
        c1[4] = 0; c1[5] = 0; c1[6] = 0; c1[7] = 0; // version = 0

        if (send_all(c1.data(), 1536) != 1536) {
            last_error_ = "Handshake: failed to send C1";
            return false;
        }

        // Read S0 (1 byte)
        uint8_t s0;
        if (recv_all(&s0, 1) != 1) {
            last_error_ = "Handshake: failed to read S0";
            return false;
        }
        if (s0 != 3) {
            last_error_ = "Handshake: server version != 3";
            return false;
        }

        // Read S1 (1536 bytes)
        std::vector<uint8_t> s1(1536);
        if (recv_all(s1.data(), 1536) != 1536) {
            last_error_ = "Handshake: failed to read S1";
            return false;
        }

        // Send C2: echo S1
        if (send_all(s1.data(), 1536) != 1536) {
            last_error_ = "Handshake: failed to send C2";
            return false;
        }

        // Read S2 (1536 bytes)
        std::vector<uint8_t> s2(1536);
        if (recv_all(s2.data(), 1536) != 1536) {
            last_error_ = "Handshake: failed to read S2";
            return false;
        }

        return true;
    }

    bool send_connect() {
        // RTMP connect command (AMF0 encoded)
        std::vector<uint8_t> payload;

        // Type 0x03 (invoke)
        // Command name: "connect"
        // Transaction ID: 1
        // Object properties

        // Write AMF0 string "connect"
        payload.push_back(0x02); // String
        write_amf_string(payload, "connect");

        // Transaction ID: 1.0
        write_amf_number(payload, 1.0);

        // Command object
        payload.push_back(0x03); // Object
        write_amf_object_property(payload, "app", app_);
        write_amf_object_property(payload, "flashVer", "FMLE/3.0 (compatible; opencode)");
        write_amf_object_property(payload, "tcUrl", "rtmp://" + host_ + "/" + app_);
        write_amf_object_property(payload, "fpad", false);
        write_amf_object_property(payload, "capabilities", 15.0);
        write_amf_object_property(payload, "audioCodecs", 3575.0);
        write_amf_object_property(payload, "videoCodecs", 252.0);
        write_amf_object_property(payload, "videoFunction", 1.0);
        payload.push_back(0x00);
        payload.push_back(0x00);
        payload.push_back(0x09); // Object end

        return send_rtmp_chunk(0x14, 0, 0, payload); // 0x14 = invoke (0x03 << 2 | 0x00)
    }

    bool send_create_stream() {
        std::vector<uint8_t> payload;
        payload.push_back(0x02); // String
        write_amf_string(payload, "createStream");
        write_amf_number(payload, 2.0); // Transaction ID
        payload.push_back(0x05); // Null
        return send_rtmp_chunk(0x14, 0, 0, payload);
    }

    bool send_publish() {
        std::vector<uint8_t> payload;
        payload.push_back(0x02); // String
        write_amf_string(payload, "publish");
        write_amf_number(payload, 0.0); // Transaction ID
        payload.push_back(0x05); // Null
        // Stream name
        payload.push_back(0x02); // String
        write_amf_string(payload, stream_key_);
        // Type: "live"
        payload.push_back(0x02); // String
        write_amf_string(payload, "live");

        // Send on stream 0x14 (invoke)
        send_rtmp_chunk(0x14, 0, 0, payload);

        // Send as stream data (chunk stream 0x08, type 0x19)
        // FLV audio/video data will be sent on this stream
        stream_id_ = 1; // Assume stream ID 1

        std::cout << "[RTMP] Published stream: " << stream_key_ << std::endl;
        return true;
    }

    bool send_rtmp_chunk(uint8_t header_type, uint32_t stream_id,
                         uint32_t timestamp, const std::vector<uint8_t>& payload) {
        // Basic header: 1 byte (fmt = 0, cs id)
        uint8_t basic_header = header_type;
        // Use CS ID 3 for command / CS ID 4 for data
        uint8_t cs_id = (header_type == 0x14 || header_type == 0x11) ? 3 : 4;
        if (cs_id < 64) {
            basic_header |= cs_id;
        }

        std::vector<uint8_t> chunk;
        chunk.push_back(basic_header);

        // Message header: 8 bytes (fmt=0)
        // Timestamp (3 bytes)
        chunk.push_back((timestamp >> 16) & 0xFF);
        chunk.push_back((timestamp >> 8) & 0xFF);
        chunk.push_back(timestamp & 0xFF);

        // Message length (3 bytes)
        uint32_t msg_len = static_cast<uint32_t>(payload.size());
        chunk.push_back((msg_len >> 16) & 0xFF);
        chunk.push_back((msg_len >> 8) & 0xFF);
        chunk.push_back(msg_len & 0xFF);

        // Message type ID (1 byte)
        chunk.push_back(header_type); // 0x14 = invoke, 0x11 = video, 0x12 = audio

        // Stream ID (4 bytes, little-endian)
        uint32_t stream_id_val = stream_id;
        chunk.push_back(stream_id_val & 0xFF);
        chunk.push_back((stream_id_val >> 8) & 0xFF);
        chunk.push_back((stream_id_val >> 16) & 0xFF);
        chunk.push_back((stream_id_val >> 24) & 0xFF);

        // Extended timestamp if needed
        if (timestamp >= 0xFFFFFF) {
            chunk.push_back((timestamp >> 24) & 0xFF);
            chunk.push_back((timestamp >> 16) & 0xFF);
            chunk.push_back((timestamp >> 8) & 0xFF);
            chunk.push_back(timestamp & 0xFF);
        }

        // Payload
        chunk.insert(chunk.end(), payload.begin(), payload.end());

        return send_all(chunk.data(), static_cast<int>(chunk.size())) == chunk.size();
    }

    // Write AMF0 string (2-byte length prefix + data)
    void write_amf_string(std::vector<uint8_t>& buf, const std::string& str) {
        uint16_t len = static_cast<uint16_t>(str.size());
        buf.push_back((len >> 8) & 0xFF);
        buf.push_back(len & 0xFF);
        buf.insert(buf.end(), str.begin(), str.end());
    }

    // Write AMF0 number (8-byte double, big-endian)
    void write_amf_number(std::vector<uint8_t>& buf, double value) {
        uint64_t val;
        memcpy(&val, &value, 8);
        for (int i = 7; i >= 0; i--) {
            buf.push_back((val >> (i * 8)) & 0xFF);
        }
    }

    // Write AMF0 object property (key-value pair)
    void write_amf_object_property(std::vector<uint8_t>& buf,
                                   const std::string& key,
                                   const std::string& value) {
        // Key
        uint16_t key_len = static_cast<uint16_t>(key.size());
        buf.push_back((key_len >> 8) & 0xFF);
        buf.push_back(key_len & 0xFF);
        buf.insert(buf.end(), key.begin(), key.end());
        // Value (string)
        buf.push_back(0x02);
        write_amf_string(buf, value);
    }

    void write_amf_object_property(std::vector<uint8_t>& buf,
                                   const std::string& key, double value) {
        uint16_t key_len = static_cast<uint16_t>(key.size());
        buf.push_back((key_len >> 8) & 0xFF);
        buf.push_back(key_len & 0xFF);
        buf.insert(buf.end(), key.begin(), key.end());
        buf.push_back(0x00); // Number
        write_amf_number(buf, value);
    }

    void write_amf_object_property(std::vector<uint8_t>& buf,
                                   const std::string& key, bool value) {
        uint16_t key_len = static_cast<uint16_t>(key.size());
        buf.push_back((key_len >> 8) & 0xFF);
        buf.push_back(key_len & 0xFF);
        buf.insert(buf.end(), key.begin(), key.end());
        buf.push_back(value ? 0x01 : 0x02); // Boolean
    }

    // Read server response (for connect/createStream)
    bool read_server_response() {
        // Read basic header
        uint8_t basic_header;
        if (recv_all(&basic_header, 1) != 1) return false;

        uint8_t cs_id = basic_header & 0x3F;
        uint8_t fmt = (basic_header >> 6) & 0x03;

        // Read message header (varies by fmt)
        // fmt=0: 11 bytes, fmt=1: 7 bytes, fmt=2: 3 bytes, fmt=3: 0 bytes
        int header_len = (fmt == 0) ? 11 : (fmt == 1) ? 7 : (fmt == 2) ? 3 : 0;

        std::vector<uint8_t> header(header_len);
        if (header_len > 0) {
            if (recv_all(header.data(), header_len) != header_len) return false;
        }

        // Parse message length from header (bytes 3-5 for fmt=0)
        if (fmt == 0 && header_len >= 6) {
            uint32_t msg_len = (header[3] << 16) | (header[4] << 8) | header[5];
            uint8_t msg_type = header[6];

            // Read payload
            std::vector<uint8_t> payload(msg_len);
            if (msg_len > 0) {
                if (recv_all(payload.data(), static_cast<int>(msg_len)) != msg_len) return false;
            }

            // Check for _result or _error
            if (msg_type == 0x14 && msg_len > 10) {
                // Parse AMF0 command
                if (payload.size() > 2) {
                    uint16_t cmd_len = (payload[0] << 8) | payload[1];
                    std::string cmd(payload.begin() + 2,
                                    payload.begin() + 2 + std::min<size_t>(cmd_len, payload.size() - 2));
                    std::cout << "[RTMP] Server response: " << cmd << std::endl;
                }
            }
        }

        return true;
    }

    // Send raw FLV data over RTMP chunk stream
    bool send_raw(const std::vector<uint8_t>& data) {
        if (data.empty()) return true;

        if (send_all(data.data(), static_cast<int>(data.size())) != data.size()) {
            // Connection lost, try reconnect
            std::cerr << "[RTMP] Connection lost, attempting reconnect..." << std::endl;
            connected_ = false;
            if (reconnect_count_ < config_.max_reconnects) {
                reconnect_count_++;
                std::this_thread::sleep_for(
                    std::chrono::milliseconds(config_.reconnect_interval));
                return connect();
            }
            return false;
        }

        bytes_sent_ += data.size();

        // Calculate bitrate
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now() - start_time_).count();
        if (elapsed > 0) {
            stats_.bitrate_kbps = (bytes_sent_ * 8.0) / elapsed;
        }

        return true;
    }

    // ==================== Socket helpers ====================
    int send_all(const void* data, int len) {
#ifdef _WIN32
        return ::send(sock_, static_cast<const char*>(data), len, 0);
#else
        return ::write(sock_, data, len);
#endif
    }

    int recv_all(void* buf, int len) {
#ifdef _WIN32
        return ::recv(sock_, static_cast<char*>(buf), len, 0);
#else
        return ::read(sock_, buf, len);
#endif
    }

    uint32_t stream_id_ = 0;
};

// ==================== Public API ====================
RTMPStreamer::RTMPStreamer() : impl_(std::make_unique<Impl>()) {}
RTMPStreamer::~RTMPStreamer() = default;

bool RTMPStreamer::init(const RTMPConfig& config) { return impl_->init(config); }
bool RTMPStreamer::connect() { return impl_->connect(); }
bool RTMPStreamer::disconnect() { return impl_->disconnect(); }
bool RTMPStreamer::send_packet(const EncodedPacket& packet) { return impl_->send_packet(packet); }
bool RTMPStreamer::is_connected() const { return impl_->is_connected(); }
void RTMPStreamer::set_status_callback(std::function<void(const std::string&)> cb) {
    impl_->status_callback_ = std::move(cb);
}
std::string RTMPStreamer::last_error() const { return impl_->last_error_; }
RTMPStreamer::RTMPStats RTMPStreamer::get_stats() const { return impl_->stats_; }
