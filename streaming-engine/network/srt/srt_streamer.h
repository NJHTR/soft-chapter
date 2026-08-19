#pragma once

#include "network/network.h"

// SRT (Secure Reliable Transport) Streamer
// TikTok's primary streaming protocol
// - Reliable packet delivery with retransmission
// - Configurable latency (120-200ms typical)
// - AES-128/256 encryption
// - End-to-end statistics for adaptive bitrate

class SRTStreamer : public NetworkStreamer {
public:
    SRTStreamer();
    ~SRTStreamer() override;

    bool init(const StreamConfig& config) override;
    bool connect() override;
    bool disconnect() override;
    bool send_packet(const uint8_t* data, size_t size,
                     int64_t timestamp, bool is_keyframe) override;

    bool is_connected() const override;
    StreamProtocol protocol() const override { return StreamProtocol::SRT; }
    double rtt_ms() const override;
    double packet_loss() const override;

    // Get SRT statistics for adaptive bitrate
    int64_t bytes_sent() const;
    int64_t bytes_retransmitted() const;
    double packet_loss_rate() const;
    double bandwidth_mbps() const;

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};
