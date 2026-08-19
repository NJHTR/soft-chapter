#pragma once

#include <vector>
#include <cstdint>
#include <memory>
#include <unordered_map>

// Forward Error Correction for live streaming
// Reduces packet loss recovery time vs. retransmission (NACK)
//
// TikTok uses:
//   - Reed-Solomon FEC for critical frames (keyframes)
//   - XOR parity for P-frames
//   - NACK retransmission as fallback

enum class FECScheme {
    None,
    XOR_1D,         // Simple 1D XOR parity
    XOR_2D,         // 2D XOR parity (better burst protection)
    ReedSolomon,    // RS(k, n) - strong protection
    LDPC,           // Low-density parity check
};

struct FECConfig {
    FECScheme scheme = FECScheme::ReedSolomon;
    int k = 10;      // Number of data packets per group
    int n = 12;      // Total packets (data + parity) per group
    int max_payload_size = 1400;  // MTU-safe
};

class FECEncoder {
public:
    FECEncoder();
    ~FECEncoder();

    bool init(const FECConfig& config);
    void encode(const uint8_t* data, size_t size,
                std::vector<std::vector<uint8_t>>& packets);
    void shutdown();

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

class FECDecoder {
public:
    FECDecoder();
    ~FECDecoder();

    bool init(const FECConfig& config);
    bool decode(const std::vector<std::vector<uint8_t>>& packets,
                std::vector<uint8_t>& output);
    void reset();
    void shutdown();

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

// NACK/ARQ Module
class NACKHandler {
public:
    NACKHandler();
    ~NACKHandler();

    void set_timeout_ms(int timeout_ms) { timeout_ms_ = timeout_ms; }
    void set_max_retries(int max_retries) { max_retries_ = max_retries; }

    // Track sent packet for potential retransmission
    void track_packet(uint16_t seq_num, const uint8_t* data, size_t size);

    // Handle incoming NACK request
    bool handle_nack(uint16_t seq_num, std::vector<uint8_t>& retransmit_data);

    // Cleanup old packets
    void cleanup(int64_t now_ms);

private:
    int timeout_ms_ = 100;
    int max_retries_ = 3;
    int64_t last_cleanup_ = 0;
    std::unordered_map<uint16_t, std::vector<uint8_t>> sent_packets_;
    std::unordered_map<uint16_t, int> retry_counts_;
};
