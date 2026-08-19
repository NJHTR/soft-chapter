#include "network/network.h"
#include "network/rtmp_streamer.h"
#include <iostream>

std::unique_ptr<NetworkStreamer> create_network_streamer(const StreamConfig& config) {
    switch (config.protocol) {
        case StreamProtocol::SRT:
#ifdef ENABLE_SRT
            return std::make_unique<SRTStreamer>();
#else
            std::cerr << "SRT not available in this build" << std::endl;
            return nullptr;
#endif

        case StreamProtocol::WebRTC:
        case StreamProtocol::WHIP:
#ifdef ENABLE_WEBRTC
            return std::make_unique<WebRTCStreamer>();
#else
            std::cerr << "WebRTC not available in this build" << std::endl;
            return nullptr;
#endif

        case StreamProtocol::RTMP:
        case StreamProtocol::HTTP_FLV:
            std::cout << "RTMP streamer needs adapter, use RTMPStreamer directly" << std::endl;
            return nullptr;

        case StreamProtocol::RIST:
        case StreamProtocol::QUIC:
            std::cerr << "Protocol not yet implemented" << std::endl;
            return nullptr;

        case StreamProtocol::LL_HLS:
            std::cout << "LL-HLS selected (via SRS)" << std::endl;
            return nullptr;

        default:
            std::cerr << "Unknown stream protocol" << std::endl;
            return nullptr;
    }
}
