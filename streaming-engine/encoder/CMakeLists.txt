add_library(streaming_engine_encoder
    encoder_factory.cpp
    bitrate_controller.cpp
    audio_encoder.cpp
)

target_include_directories(streaming_engine_encoder
    PUBLIC
    ${CMAKE_SOURCE_DIR}
    ${FFMPEG_INCLUDE_DIRS}
)
target_link_libraries(streaming_engine_encoder PUBLIC streaming_engine_common)

if(ENABLE_NVENC)
    target_sources(streaming_engine_encoder PRIVATE nvenc/nvenc_encoder.cpp)
    target_compile_definitions(streaming_engine_encoder PRIVATE ENABLE_NVENC)
endif()

target_sources(streaming_engine_encoder PRIVATE x264/x264_encoder.cpp)
target_link_libraries(streaming_engine_encoder PRIVATE ${FFMPEG_LIBRARIES})
