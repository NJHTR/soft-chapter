#pragma once

#include "common/frame.h"
#include "gpu/gpu_pipeline.h"
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

// GPU Shader Manager
// Loads, compiles, and manages GLSL shader programs for the GPU pipeline
// Supports: OpenGL 4.6, Vulkan 1.3, Metal (via SPIR-V cross), CUDA

class ShaderManager {
public:
    ShaderManager();
    ~ShaderManager();

    bool init(GPUApi api);
    void shutdown();

    // Load shader from GLSL source
    bool load_shader(const std::string& name, const std::string& vertex_src,
                     const std::string& fragment_src);
    bool load_compute(const std::string& name, const std::string& compute_src);

    // Load shader from file
    bool load_shader_file(const std::string& name, const std::string& vertex_path,
                          const std::string& fragment_path);
    bool load_compute_file(const std::string& name, const std::string& path);

    // Built-in shaders
    enum BuiltInShader {
        OVERSCALE_LANCZOS3,
        BEAUTY_SKIN_SMOOTH,
        BEAUTY_EYE_ENHANCE,
        BEAUTY_LIP_ENHANCE,
        HDR_TONE_MAP,
        SHARPEN_GUIDED_FILTER,
        COLOR_3D_LUT,
        NV12_TO_RGBA,
        RGBA_TO_NV12,
    };

    bool load_builtin(BuiltInShader shader);

    // Bind shader for use
    bool bind(const std::string& name);
    bool bind_builtin(BuiltInShader shader);

    // Set uniforms
    void set_uniform(const std::string& name, int value);
    void set_uniform(const std::string& name, float value);
    void set_uniform(const std::string& name, const float* matrix4x4);
    void set_uniform(const std::string& name, int count, const float* values);

    // Bind textures
    bool bind_input_texture(uint32_t unit, uint32_t texture_id);
    bool bind_output_image(uint32_t unit, uint32_t texture_id, uint32_t level);

    // Dispatch
    bool dispatch_compute(uint32_t groups_x, uint32_t groups_y, uint32_t groups_z);
    bool draw_fullscreen_quad();

    // Create textures for pipeline
    uint32_t create_texture(int width, int height, PixelFormat format, bool gpu_only = false);
    void destroy_texture(uint32_t texture_id);

    // Debug
    std::string last_error() const { return last_error_; }

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
    std::string last_error_;
    GPUApi api_;
};

// ===== Built-in shader source files =====
// These are loaded from streaming-engine/gpu/shader/*.glsl
// The ShaderManager reads the GLSL files and compiles them at runtime

// For reference, the shader files are:
//   oversample.glsl    → GPUOversampler (4K Lanczos3 downscale)
//   beauty.glsl        → BeautyPipeline (skin, eye, lip enhancement)
//   hdr_tone_map.glsl  → HDR tonemapping (ACES, BT.709, BT.2020, HLG, PQ)
//   sharpen.glsl       → Edge-aware sharpen (guided filter)
//   3d_lut.glsl        → 3D LUT color grading (ACES, film LUTs)
