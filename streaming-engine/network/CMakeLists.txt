add_library(streaming_engine_network
    network_factory.cpp
    fecc/fec.cpp
    muxer.cpp
    rtmp_streamer.cpp
)

target_include_directories(streaming_engine_network PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_network
    PUBLIC
    streaming_engine_common
    streaming_engine_encoder
)

if(ENABLE_SRT)
    target_sources(streaming_engine_network PRIVATE srt/srt_streamer.cpp)
    target_link_libraries(streaming_engine_network PRIVATE srt::srt)
    target_compile_definitions(streaming_engine_network PRIVATE ENABLE_SRT)
endif()

if(ENABLE_WEBRTC)
    target_sources(streaming_engine_network PRIVATE webrtc/webrtc_streamer.cpp)
    target_compile_definitions(streaming_engine_network PRIVATE ENABLE_WEBRTC)
endif()
