add_library(streaming_engine_ai
    ai_factory.cpp
)

target_include_directories(streaming_engine_ai PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_ai PUBLIC streaming_engine_common)

if(ENABLE_TENSORRT)
    target_sources(streaming_engine_ai PRIVATE tensorrt/tensorrt_engine.cpp)
    target_link_libraries(streaming_engine_ai PRIVATE ${TENSORRT_LIB})
    target_include_directories(streaming_engine_ai PRIVATE ${TENSORRT_INCLUDE})
    target_compile_definitions(streaming_engine_ai PRIVATE ENABLE_TENSORRT)
endif()

if(ENABLE_ONNX AND onnxruntime_FOUND)
    target_link_libraries(streaming_engine_ai PRIVATE onnxruntime::onnxruntime)
    target_compile_definitions(streaming_engine_ai PRIVATE ENABLE_ONNX)
endif()
