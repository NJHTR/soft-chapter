# AI Super Resolution module (uses models from ai/ via TensorRT/ONNX)
# Model implementations are in ai/ module
# This module provides the SR pipeline wrapper

add_library(streaming_engine_super_res INTERFACE)
target_include_directories(streaming_engine_super_res INTERFACE ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_super_res INTERFACE streaming_engine_ai)
