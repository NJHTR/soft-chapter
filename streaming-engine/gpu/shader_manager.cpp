#include "gpu/shader_manager.h"
#include <iostream>
#include <fstream>
#include <sstream>
#include <vector>
#include <cstring>

#ifdef _WIN32
#include <windows.h>
#include <gl/gl.h>
#endif

class ShaderManager::Impl {
public:
    GPUApi api_;
    std::unordered_map<std::string, uint32_t> programs_;  // name → GL program
    uint32_t current_program_ = 0;

    // Built-in shader file paths
    static constexpr const char* SHADER_DIR = "streaming-engine/gpu/shader/";

    struct BuiltInInfo {
        BuiltInShader id;
        const char* name;
        const char* file;
        bool is_compute;
    };

    static const BuiltInInfo builtins_[10];

    bool init(GPUApi api) {
        api_ = api;
        std::cout << "ShaderManager initialized (api="
                  << static_cast<int>(api) << ")" << std::endl;
        return true;
    }

    uint32_t compile_glsl(GLenum type, const std::string& source) {
#ifdef ENABLE_OPENGL
        uint32_t shader = glCreateShader(type);
        const char* src = source.c_str();
        glShaderSource(shader, 1, &src, nullptr);
        glCompileShader(shader);

        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            char info[1024];
            glGetShaderInfoLog(shader, sizeof(info), nullptr, info);
            std::cerr << "[Shader] Compile error: " << info << std::endl;
            glDeleteShader(shader);
            return 0;
        }
        return shader;
#else
        return 0;
#endif
    }

    uint32_t link_program(uint32_t vs, uint32_t fs) {
#ifdef ENABLE_OPENGL
        uint32_t prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);

        GLint linked = 0;
        glGetProgramiv(prog, GL_LINK_STATUS, &linked);
        if (!linked) {
            char info[1024];
            glGetProgramInfoLog(prog, sizeof(info), nullptr, info);
            std::cerr << "[Shader] Link error: " << info << std::endl;
            glDeleteProgram(prog);
            return 0;
        }

        // Clean up shaders after linking
        glDeleteShader(vs);
        glDeleteShader(fs);

        return prog;
#else
        return 0;
#endif
    }

    bool load_shader(const std::string& name, const std::string& vs,
                     const std::string& fs) {
#ifdef ENABLE_OPENGL
        uint32_t vs_id = compile_glsl(GL_VERTEX_SHADER, vs);
        uint32_t fs_id = compile_glsl(GL_FRAGMENT_SHADER, fs);
        if (!vs_id || !fs_id) return false;

        uint32_t prog = link_program(vs_id, fs_id);
        if (!prog) return false;

        programs_[name] = prog;
        std::cout << "[Shader] Loaded: " << name << " (ID=" << prog << ")" << std::endl;
        return true;
#else
        std::cerr << "[Shader] OpenGL not enabled, can't load: " << name << std::endl;
        return false;
#endif
    }

    bool load_from_file(const std::string& name, const std::string& path) {
        // Read file
        std::ifstream file(path);
        if (!file.is_open()) {
            std::cerr << "[Shader] Cannot open file: " << path << std::endl;
            return false;
        }
        std::stringstream ss;
        ss << file.rdbuf();
        std::string source = ss.str();

        // For fullscreen quad shaders, use built-in vertex shader
        static const char* fullscreen_vs = R"(
            #version 460 core
            layout(location = 0) in vec2 aPos;
            layout(location = 1) in vec2 aUV;
            out vec2 uv;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                uv = aUV;
            }
        )";

        return load_shader(name, fullscreen_vs, source);
    }

    bool load_builtin(BuiltInShader shader) {
        for (const auto& b : builtins_) {
            if (b.id == shader) {
                std::string path = std::string(SHADER_DIR) + b.file;
                return load_from_file(b.name, path);
            }
        }
        return false;
    }

    bool bind(const std::string& name) {
        auto it = programs_.find(name);
        if (it == programs_.end()) {
            std::cerr << "[Shader] Not found: " << name << std::endl;
            return false;
        }
#ifdef ENABLE_OPENGL
        glUseProgram(it->second);
        current_program_ = it->second;
#endif
        return true;
    }

    void set_uniform_int(const std::string& name, int value) {
#ifdef ENABLE_OPENGL
        if (!current_program_) return;
        GLint loc = glGetUniformLocation(current_program_, name.c_str());
        if (loc >= 0) glUniform1i(loc, value);
#endif
    }

    void set_uniform_float(const std::string& name, float value) {
#ifdef ENABLE_OPENGL
        if (!current_program_) return;
        GLint loc = glGetUniformLocation(current_program_, name.c_str());
        if (loc >= 0) glUniform1f(loc, value);
#endif
    }

    void shutdown() {
#ifdef ENABLE_OPENGL
        for (auto& [name, prog] : programs_) {
            glDeleteProgram(prog);
        }
#endif
        programs_.clear();
    }
};

const ShaderManager::Impl::BuiltInInfo ShaderManager::Impl::builtins_[10] = {
    {BuiltInShader::OVERSCALE_LANCZOS3,   "oversample",    "oversample.glsl",   false},
    {BuiltInShader::BEAUTY_SKIN_SMOOTH,   "beauty",        "beauty.glsl",       false},
    {BuiltInShader::HDR_TONE_MAP,         "hdr_tone_map",  "hdr_tone_map.glsl", false},
    {BuiltInShader::SHARPEN_GUIDED_FILTER,"sharpen",       "sharpen.glsl",      false},
    {BuiltInShader::COLOR_3D_LUT,         "3d_lut",        "3d_lut.glsl",       false},
    // Remaining entries are zero-initialized
};

ShaderManager::ShaderManager() : impl_(std::make_unique<Impl>()) {}
ShaderManager::~ShaderManager() = default;

bool ShaderManager::init(GPUApi api) { return impl_->init(api); }
void ShaderManager::shutdown() { impl_->shutdown(); }

bool ShaderManager::load_shader(const std::string& name, const std::string& vs,
                                 const std::string& fs) {
    return impl_->load_shader(name, vs, fs);
}

bool ShaderManager::load_builtin(BuiltInShader shader) {
    return impl_->load_builtin(shader);
}

bool ShaderManager::bind(const std::string& name) { return impl_->bind(name); }
bool ShaderManager::bind_builtin(BuiltInShader shader) {
    for (const auto& b : Impl::builtins_) {
        if (b.id == shader) return bind(b.name);
    }
    return false;
}

void ShaderManager::set_uniform(const std::string& name, int value) {
    impl_->set_uniform_int(name, value);
}
void ShaderManager::set_uniform(const std::string& name, float value) {
    impl_->set_uniform_float(name, value);
}
