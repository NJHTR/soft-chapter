#include "ai/ai_engine.h"
#include <iostream>
#include <unordered_map>

// ===== Model registry for loading AI models =====
class AIModelRegistry {
public:
    using ModelFactory = std::function<std::unique_ptr<AIModel>()>;
    std::unordered_map<std::string, ModelFactory> factories_;

    static AIModelRegistry& instance() {
        static AIModelRegistry reg;
        return reg;
    }

    void register_model(const std::string& name, ModelFactory factory) {
        factories_[name] = std::move(factory);
    }

    std::unique_ptr<AIModel> create(const std::string& name) {
        auto it = factories_.find(name);
        if (it != factories_.end()) {
            return it->second();
        }
        return nullptr;
    }
};

// ===== Generic ONNX Model =====
class ONNXModel : public AIModel {
public:
    AIModelConfig config_;
    AIModelType type_;
    AIBackend backend_;

    ONNXModel(AIModelType type, AIBackend backend)
        : type_(type), backend_(backend) {}

    bool load(const AIModelConfig& config) override {
        config_ = config;
        std::cout << "ONNX model loading: " << config.model_path
                  << " [" << (config.use_fp16 ? "FP16" : "FP32") << "]"
                  << std::endl;
        // In production: OrtSession::Run()
        return true;
    }

    bool infer(std::shared_ptr<Frame> input,
               std::shared_ptr<Frame>& output) override {
        if (!input) return false;

        // Create output with same dimensions
        if (!output) {
            output = Frame::create_cpu(
                input->buffer().width,
                input->buffer().height,
                input->buffer().format);
        }

        // Copy input to output (placeholder - real inference runs in GPU)
        if (input->buffer().data && output->buffer().data) {
            size_t copy_size = std::min(input->buffer().size, output->buffer().size);
            memcpy(output->buffer().data, input->buffer().data, copy_size);
        }

        output->buffer().timestamp = input->buffer().timestamp;
        return true;
    }

    void shutdown() override {
        std::cout << "ONNX model shutdown" << std::endl;
    }

    AIModelType type() const override { return type_; }
    AIBackend backend() const override { return backend_; }
};

// ===== TensorRT Model =====
#ifdef ENABLE_TENSORRT
class TensorRTModel : public AIModel {
public:
    AIModelConfig config_;
    AIModelType type_;
    void* engine_ = nullptr;
    void* context_ = nullptr;
    void* cuda_stream_ = nullptr;

    TensorRTModel(AIModelType type) : type_(type) {}

    bool load(const AIModelConfig& config) override {
        config_ = config;
        std::cout << "TensorRT model loading: " << config.model_path
                  << " [FP16:" << (config.use_fp16 ? "ON" : "OFF")
                  << " INT8:" << (config.use_int8 ? "ON" : "OFF") << "]"
                  << std::endl;
        // In production:
        // 1. deserializeCudaEngine(model_file)
        // 2. createExecutionContext()
        // 3. setTensorAddress()
        return true;
    }

    bool infer(std::shared_ptr<Frame> input,
               std::shared_ptr<Frame>& output) override {
        if (!input) return false;
        if (!output) {
            output = Frame::create_cpu(
                input->buffer().width,
                input->buffer().height,
                input->buffer().format);
        }
        output->buffer().timestamp = input->buffer().timestamp;
        return true;
    }

    void shutdown() override {
        std::cout << "TensorRT model shutdown" << std::endl;
    }

    AIModelType type() const override { return type_; }
    AIBackend backend() const override { return AIBackend::TensorRT; }
};
#endif

// ===== NCNN Model =====
#ifdef ENABLE_NCNN
class NCNNModel : public AIModel { /* ... */ };
#endif

// ===== Factory registration =====
static void register_ai_models();

// Static initializer (cross-platform)
namespace {
    struct Registrar {
        Registrar() { register_ai_models(); }
    } registrar;
}

static void register_ai_models() {
    auto& reg = AIModelRegistry::instance();

    reg.register_model("denoise_fastdvdnet", []() {
#ifdef ENABLE_TENSORRT
        return std::make_unique<TensorRTModel>(AIModelType::Denoise);
#else
        return std::make_unique<ONNXModel>(AIModelType::Denoise, AIBackend::ONNX);
#endif
    });

    reg.register_model("denoise_nafnet", []() {
        return std::make_unique<ONNXModel>(AIModelType::Denoise, AIBackend::ONNX);
    });

    reg.register_model("super_res_realesrgan", []() {
#ifdef ENABLE_TENSORRT
        return std::make_unique<TensorRTModel>(AIModelType::SuperResolution);
#else
        return std::make_unique<ONNXModel>(AIModelType::SuperResolution, AIBackend::ONNX);
#endif
    });

    reg.register_model("super_res_swinir", []() {
        return std::make_unique<ONNXModel>(AIModelType::SuperResolution, AIBackend::ONNX);
    });

    reg.register_model("face_mediapipe", []() {
        return std::make_unique<ONNXModel>(AIModelType::FaceDetection, AIBackend::ONNX);
    });

    reg.register_model("face_insightface", []() {
#ifdef ENABLE_TENSORRT
        return std::make_unique<TensorRTModel>(AIModelType::FaceLandmark);
#else
        return std::make_unique<ONNXModel>(AIModelType::FaceLandmark, AIBackend::ONNX);
#endif
    });
}

// ===== Public API to create any model =====
std::unique_ptr<AIModel> create_ai_model(const std::string& model_name,
                                          AIBackend preferred_backend) {
    return AIModelRegistry::instance().create(model_name);
}
