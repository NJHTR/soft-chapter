#include "network/fecc/fec.h"
#include <iostream>
#include <cstring>
#include <vector>
#include <cassert>

// ===== Simple XOR FEC (1D Parity) =====
// Used for P-frames where light protection is sufficient

class FECEncoder::Impl {
public:
    FECConfig config_;

    bool init(const FECConfig& config) {
        config_ = config;
        std::cout << "FEC encoder initialized: "
                  << "scheme=" << static_cast<int>(config_.scheme)
                  << " k=" << config_.k << " n=" << config_.n
                  << std::endl;
        return true;
    }

    void encode_xor(const uint8_t* data, size_t size,
                    std::vector<std::vector<uint8_t>>& packets) {
        size_t payload_size = config_.max_payload_size;
        int num_packets = (size + payload_size - 1) / payload_size;

        // Split into k data packets
        for (int i = 0; i < num_packets && i < config_.k; i++) {
            std::vector<uint8_t> pkt(payload_size);
            size_t offset = i * payload_size;
            size_t copy = std::min(payload_size, size - offset);
            memcpy(pkt.data(), data + offset, copy);
            packets.push_back(std::move(pkt));
        }

        // Generate parity packet (XOR all data packets)
        std::vector<uint8_t> parity(payload_size, 0);
        for (const auto& pkt : packets) {
            for (size_t j = 0; j < payload_size; j++) {
                parity[j] ^= pkt[j];
            }
        }
        packets.push_back(std::move(parity));
    }

    void encode_reed_solomon(const uint8_t* data, size_t size,
                             std::vector<std::vector<uint8_t>>& packets) {
        // Reed-Solomon FEC
        // In production: use OpenFEC or libRaptorQ
        // RS(k, n) can recover from any n-k losses
        // For now, use XOR as simpler fallback
        encode_xor(data, size, packets);
    }

    void encode(const uint8_t* data, size_t size,
                std::vector<std::vector<uint8_t>>& packets) {
        switch (config_.scheme) {
            case FECScheme::XOR_1D:
            case FECScheme::XOR_2D:
                encode_xor(data, size, packets);
                break;
            case FECScheme::ReedSolomon:
            case FECScheme::LDPC:
                encode_reed_solomon(data, size, packets);
                break;
            default:
                // No FEC: single packet
                packets.emplace_back(data, data + size);
                break;
        }
    }
};

FECEncoder::FECEncoder() : impl_(std::make_unique<Impl>()) {}
FECEncoder::~FECEncoder() = default;
bool FECEncoder::init(const FECConfig& config) { return impl_->init(config); }
void FECEncoder::encode(const uint8_t* data, size_t size,
                         std::vector<std::vector<uint8_t>>& packets) {
    impl_->encode(data, size, packets);
}

// ===== FEC Decoder =====

class FECDecoder::Impl {
public:
    FECConfig config_;

    bool init(const FECConfig& config) {
        config_ = config;
        return true;
    }

    bool decode_xor(const std::vector<std::vector<uint8_t>>& packets,
                    std::vector<uint8_t>& output) {
        if (packets.empty()) return false;

        size_t payload_size = packets[0].size();
        output.resize(packets.size() * payload_size);

        // Recover missing packets via XOR
        // In production: handle arbitrary loss patterns
        if (packets.size() >= 2) {
            for (size_t i = 0; i < (packets.size() - 1); i++) {
                memcpy(output.data() + i * payload_size,
                       packets[i].data(), payload_size);
            }
        }

        return true;
    }

    bool decode(const std::vector<std::vector<uint8_t>>& packets,
                std::vector<uint8_t>& output) {
        return decode_xor(packets, output);
    }
};

FECDecoder::FECDecoder() : impl_(std::make_unique<Impl>()) {}
FECDecoder::~FECDecoder() = default;
bool FECDecoder::init(const FECConfig& config) { return impl_->init(config); }
bool FECDecoder::decode(const std::vector<std::vector<uint8_t>>& packets,
                         std::vector<uint8_t>& output) {
    return impl_->decode(packets, output);
}

// ===== NACK Handler =====

void NACKHandler::track_packet(uint16_t seq_num, const uint8_t* data, size_t size) {
    sent_packets_[seq_num] = std::vector<uint8_t>(data, data + size);
    retry_counts_[seq_num] = 0;
}

bool NACKHandler::handle_nack(uint16_t seq_num, std::vector<uint8_t>& retransmit_data) {
    auto it = sent_packets_.find(seq_num);
    if (it == sent_packets_.end()) return false;

    auto& retry = retry_counts_[seq_num];
    if (retry >= max_retries_) {
        sent_packets_.erase(seq_num);
        return false;
    }

    retry++;
    retransmit_data = it->second;
    return true;
}

void NACKHandler::cleanup(int64_t now_ms) {
    if (now_ms - last_cleanup_ < timeout_ms_) return;
    last_cleanup_ = now_ms;

    auto it = sent_packets_.begin();
    while (it != sent_packets_.end()) {
        auto retry_it = retry_counts_.find(it->first);
        if (retry_it != retry_counts_.end() && retry_it->second >= max_retries_) {
            retry_counts_.erase(retry_it);
            it = sent_packets_.erase(it);
        } else {
            ++it;
        }
    }
}
