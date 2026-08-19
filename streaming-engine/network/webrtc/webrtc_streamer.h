#pragma once

#include "network/network.h"

// WebRTC Streamer for ultra-low-latency streaming (<100ms)
// Uses: libdatachannel or Google's WebRTC stack
// - PeerConnection for P2P streaming
// - WHIP for ingest
// - Simulcast for multi-quality layers

class WebRTCStreamer : public NetworkStreamer {
public:
    WebRTCStreamer();
    ~WebRTCStreamer() override;

    bool init(const StreamConfig& config) override;
    bool connect() override;
    bool disconnect() override;
    bool send_packet(const uint8_t* data, size_t size,
                     int64_t timestamp, bool is_keyframe) override;

    bool is_connected() const override;
    StreamProtocol protocol() const override { return StreamProtocol::WebRTC; }
    double rtt_ms() const override;
    double packet_loss() const override;

    // Simulcast layers for adaptive quality
    bool configure_simulcast(int num_layers = 3);
    bool set_encoding_parameters(int bitrate_kbps, int fps);

    // TWCC (Transport Wide Congestion Control)
    void enable_twcc(bool enable);

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
