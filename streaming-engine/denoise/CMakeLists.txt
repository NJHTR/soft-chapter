# AI Denoise module (uses models from ai/ via TensorRT/ONNX)
# Model implementations are in ai/ module
# This module provides the denoise pipeline wrapper

add_library(streaming_engine_denoise INTERFACE)
target_include_directories(streaming_engine_denoise INTERFACE ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_denoise INTERFACE streaming_engine_ai)
