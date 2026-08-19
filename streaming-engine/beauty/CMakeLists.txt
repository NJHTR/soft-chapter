add_library(streaming_engine_beauty
    beauty_pipeline.cpp
)

target_include_directories(streaming_engine_beauty PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_beauty
    PUBLIC
    streaming_engine_common
    streaming_engine_gpu
    streaming_engine_ai
)
