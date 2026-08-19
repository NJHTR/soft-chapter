cmake_minimum_required(VERSION 3.25)
project(streaming-engine VERSION 1.0.0 LANGUAGES CXX C)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ====== Options ======
option(ENABLE_CUDA "Enable CUDA support" ON)
option(ENABLE_VULKAN "Enable Vulkan support" ON)
option(ENABLE_METAL "Enable Metal support (macOS/iOS)" OFF)
option(ENABLE_TENSORRT "Enable TensorRT AI" ON)
option(ENABLE_ONNX "Enable ONNX Runtime" ON)
option(ENABLE_NCNN "Enable NCNN backend" OFF)
option(ENABLE_SRT "Enable SRT protocol" ON)
option(ENABLE_WEBRTC "Enable WebRTC" ON)
option(ENABLE_NVENC "Enable NVIDIA NVENC" ON)
option(ENABLE_QSV "Enable Intel QuickSync" OFF)
option(ENABLE_SERVER "Build streaming server integration" ON)
option(ENABLE_TESTS "Build tests" ON)

# ====== Dependencies ======
find_package(PkgConfig QUIET)

# CUDA
if(ENABLE_CUDA)
    find_package(CUDA 12 QUIET)
    if(CUDA_FOUND)
        add_definitions(-DENABLE_CUDA)
        set(CUDA_GENERATION "Auto")
        enable_language(CUDA)
    endif()
endif()

# OpenGL
find_package(OpenGL REQUIRED)

# Vulkan
if(ENABLE_VULKAN)
    find_package(Vulkan REQUIRED)
    add_definitions(-DENABLE_VULKAN)
endif()

# TensorRT
if(ENABLE_TENSORRT)
    find_library(TENSORRT_LIB nvinfer nvinfer_plugin)
    find_path(TENSORRT_INCLUDE NvInfer.h)
    if(TENSORRT_LIB AND TENSORRT_INCLUDE)
        add_definitions(-DENABLE_TENSORRT)
    endif()
endif()

# ONNX Runtime
if(ENABLE_ONNX)
    find_package(onnxruntime QUIET)
    if(onnxruntime_FOUND)
        add_definitions(-DENABLE_ONNX)
    endif()
endif()

# FFmpeg
find_package(FFmpeg REQUIRED COMPONENTS avcodec avformat avutil swscale avfilter)

# SRT
if(ENABLE_SRT)
    find_package(srt QUIET)
    if(srt_FOUND)
        add_definitions(-DENABLE_SRT)
    endif()
endif()

# OpenCV
find_package(OpenCV REQUIRED COMPONENTS core imgproc)

# ====== Sources ======
add_subdirectory(capture)
add_subdirectory(gpu)
add_subdirectory(ai)
add_subdirectory(beauty)
add_subdirectory(denoise)
add_subdirectory(super_resolution)
add_subdirectory(encoder)
add_subdirectory(network)
add_subdirectory(server)
add_subdirectory(player)
add_subdirectory(common)

if(ENABLE_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()
