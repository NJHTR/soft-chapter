@echo off
REM Build script for streaming-engine on Windows
REM Usage: tools\build.bat [Debug|Release]

setlocal enabledelayedexpansion

set BUILD_TYPE=%1
if "%BUILD_TYPE%"=="" set BUILD_TYPE=Release

set BUILD_DIR=build

echo === Checking Dependencies ===

where cmake >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: cmake not found. Install cmake ^>= 3.25
    exit /b 1
)

echo CMake found

REM Check for CUDA
where nvcc >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    nvcc --version | find "release" >nul
    echo CUDA found
    set CUDA_OPT=
    set NVENC_OPT=
) else (
    echo CUDA not found - building without GPU acceleration
    set CUDA_OPT=-DENABLE_CUDA=OFF -DENABLE_TENSORRT=OFF -DENABLE_NVENC=OFF
)

REM Check for NVENC SDK header
if not defined NVENC_OPT (
    if not exist "%CUDA_PATH%\include\nvEncodeAPI.h" (
        echo nvEncodeAPI.h not found - disabling NVENC
        set NVENC_OPT=-DENABLE_NVENC=OFF
    ) else (
        echo NVENC SDK found
        set NVENC_OPT=
    )
)

REM Check for Vulkan
where glslangValidator >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Vulkan SDK found
    set VK_OPT=
) else (
    echo Vulkan not found - disabling Vulkan support
    set VK_OPT=-DENABLE_VULKAN=OFF
)

REM Check for vcpkg
set VCPKG_ROOT=C:\dev\vcpkg
if exist "%VCPKG_ROOT%\vcpkg.exe" (
    echo vcpkg found at %VCPKG_ROOT%
    set CMAKE_TOOLCHAIN_FILE=-DCMAKE_TOOLCHAIN_FILE=%VCPKG_ROOT%\scripts\buildsystems\vcpkg.cmake
) else (
    echo vcpkg not found
    set CMAKE_TOOLCHAIN_FILE=
)

echo.
echo === Configuring ===

cmake -B %BUILD_DIR% ^
    -DCMAKE_BUILD_TYPE=%BUILD_TYPE% ^
    %CUDA_OPT% ^
    %NVENC_OPT% ^
    %VK_OPT% ^
    %CMAKE_TOOLCHAIN_FILE% ^
    -DENABLE_TESTS=ON

echo.
echo === Building ===

cmake --build %BUILD_DIR% --config %BUILD_TYPE% -j 4

echo.
echo === Running Tests ===

cd %BUILD_DIR%
ctest --output-on-failure -C %BUILD_TYPE% -j4

echo.
echo === Build Complete ===
echo Output: %BUILD_DIR%/
