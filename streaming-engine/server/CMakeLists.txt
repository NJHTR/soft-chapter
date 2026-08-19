add_library(streaming_engine_server
    streaming_server.cpp
    transcode/transcoder.cpp
)

target_include_directories(streaming_engine_server PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_server
    PUBLIC
    streaming_engine_common
    streaming_engine_encoder
    streaming_engine_network
)

# libcurl for SRS HTTP API
find_package(CURL QUIET)
if(CURL_FOUND)
    target_link_libraries(streaming_engine_server PRIVATE CURL::libcurl)
    target_compile_definitions(streaming_engine_server PRIVATE ENABLE_CURL)
endif()
