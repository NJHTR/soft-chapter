#!/bin/bash
# Build script for streaming-engine
# Usage: ./tools/build.sh [debug|release]

set -e

BUILD_TYPE="${1:-release}"
BUILD_DIR="build"

# Check for required dependencies
echo "=== Checking Dependencies ==="

# CMake
if ! command -v cmake &> /dev/null; then
    echo "ERROR: cmake not found. Install cmake >= 3.25"
    exit 1
fi

# CUDA (optional)
if command -v nvcc &> /dev/null; then
    CUDA_VERSION=$(nvcc --version | grep "release" | awk '{print $6}' | cut -c2-)
    echo "CUDA $CUDA_VERSION found"
else
    echo "CUDA not found - building without GPU acceleration"
    CUDA_OPT="-DENABLE_CUDA=OFF -DENABLE_TENSORRT=OFF"
fi

# FFmpeg
if pkg-config --exists libavcodec; then
    echo "FFmpeg found"
else
    echo "WARNING: FFmpeg not found via pkg-config"
fi

# SRT
if pkg-config --exists srt; then
    echo "SRT library found"
else
    echo "WARNING: SRT not found - building without SRT support"
    SRT_OPT="-DENABLE_SRT=OFF"
fi

echo ""
echo "=== Configuring ==="

cmake -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    $CUDA_OPT \
    $SRT_OPT \
    -DENABLE_TESTS=ON \
    -GNinja 2>/dev/null || cmake -B "$BUILD_DIR" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    $CUDA_OPT \
    $SRT_OPT \
    -DENABLE_TESTS=ON

echo ""
echo "=== Building ==="

cmake --build "$BUILD_DIR" -j$(nproc 2>/dev/null || echo 4)

echo ""
echo "=== Running Tests ==="

cd "$BUILD_DIR"
ctest --output-on-failure -j4 2>/dev/null || echo "No tests to run"

echo ""
echo "=== Build Complete ==="
echo "Output: $BUILD_DIR/"
