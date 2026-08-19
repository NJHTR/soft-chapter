add_library(streaming_engine_common
    frame.cpp
    pipeline.cpp
)

target_include_directories(streaming_engine_common PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_common PUBLIC opencv_core)
