#include "beauty/beauty_pipeline.h"
#include "gpu/gpu_pipeline.h"
#include <iostream>
#include <cmath>

class BeautyPipeline::Impl {
public:
    BeautyConfig config_;
    std::shared_ptr<Frame> output_;

    // GPU pipeline nodes
    std::unique_ptr<GPUColorConverter> color_converter_;
    std::unique_ptr<GPUOversampler> oversampler_;

    // Face detection model
    std::unique_ptr<AIModel> face_detector_;
    std::unique_ptr<AIModel> face_landmark_;

    // Face info cache
    std::vector<FaceInfo> faces_;
    int face_cache_counter_ = 0;

    bool init(const BeautyConfig& config, AIBackend face_backend) {
        config_ = config;

        // Initialize face detection
        AIModelConfig face_config;
        face_config.type = AIModelType::FaceDetection;
        face_config.backend = face_backend;
        face_config.model_path = "models/face/mediapipe_face.tensorrt";
        face_config.use_fp16 = true;
        face_config.input_width = 256;
        face_config.input_height = 256;

        face_detector_ = create_ai_model("face_mediapipe", face_backend);
        if (face_detector_) {
            face_detector_->load(face_config);
        }

        // Initialize face landmark
        AIModelConfig landmark_config;
        landmark_config.type = AIModelType::FaceLandmark;
        landmark_config.backend = face_backend;
        landmark_config.model_path = "models/face/face_landmark.tensorrt";

        face_landmark_ = create_ai_model("face_insightface", face_backend);
        if (face_landmark_) {
            face_landmark_->load(landmark_config);
        }

        std::cout << "Beauty pipeline initialized" << std::endl;
        return true;
    }

    bool process_frame(std::shared_ptr<Frame> frame,
                       const FaceInfo* face_info) {
        if (!frame) return false;

        // 1. Detect faces (if not provided externally)
        std::vector<FaceInfo> faces;
        if (face_info) {
            faces.push_back(*face_info);
        } else if (config_.enable_skin_smooth ||
                   config_.enable_eye_enhance ||
                   config_.enable_lip_enhance ||
                   config_.enable_face_slim) {
            detect_faces(frame, faces);
        }

        // 2. Apply beauty effects per face
        for (auto& face : faces) {
            if (config_.enable_skin_smooth) {
                apply_skin_smoothing(frame, face);
            }
            if (config_.enable_eye_enhance) {
                apply_eye_enhancement(frame, face);
            }
            if (config_.enable_lip_enhance) {
                apply_lip_enhancement(frame, face);
            }
            if (config_.enable_face_slim) {
                apply_face_slimming(frame, face);
            }
        }

        // 3. Apply 3D LUT color grading
        if (config_.enable_color_lut) {
            apply_3d_lut(frame);
        }

        output_ = frame;
        return true;
    }

    bool detect_faces(std::shared_ptr<Frame> frame,
                      std::vector<FaceInfo>& faces) {
        // Run face detection model on frame
        std::shared_ptr<Frame> detection_output;
        if (!face_detector_->infer(frame, detection_output)) {
            return false;
        }

        // Parse detection output to populate FaceInfo
        // In production: decode model output → face bounding boxes
        FaceInfo face;
        face.num_faces = 1;
        face.face_rect[0] = 0.2f;  // x
        face.face_rect[1] = 0.2f;  // y
        face.face_rect[2] = 0.6f;  // w
        face.face_rect[3] = 0.6f;  // h

        // Run landmark model
        if (face_landmark_) {
            face_landmark_->infer(frame, detection_output);
            // In production: 468 or 478 landmarks decoded here
            for (int i = 0; i < 468; i++) {
                face.landmarks[i][0] = 0.5f;
                face.landmarks[i][1] = 0.5f;
            }
        }

        faces.push_back(face);
        return true;
    }

    void apply_skin_smoothing(std::shared_ptr<Frame> frame,
                              const FaceInfo& face) {
        // GPU shader-based bilateral filter
        // In production: bind frame as texture, apply beauty.glsl
        auto& buf = frame->buffer();

        // Simple CPU-based skin smoothing (for reference)
        if (buf.format == PixelFormat::RGBA && buf.data) {
            int w = buf.width;
            int h = buf.height;
            int stride = buf.stride[0];

            // Basic averaging filter (placeholder for GPU shader)
            std::vector<uint8_t> temp(buf.data, buf.data + buf.size);

            float intensity = config_.skin_smoothness;
            for (int y = 1; y < h - 1; y++) {
                for (int x = 1; x < w - 1; x++) {
                    int offset = y * stride + x * 4;
                    int r = 0, g = 0, b = 0, count = 0;

                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            int s = (y + dy) * stride + (x + dx) * 4;
                            r += temp[s];
                            g += temp[s + 1];
                            b += temp[s + 2];
                            count++;
                        }
                    }

                    buf.data[offset]     = static_cast<uint8_t>(
                        temp[offset] * (1 - intensity) + (r / count) * intensity);
                    buf.data[offset + 1] = static_cast<uint8_t>(
                        temp[offset + 1] * (1 - intensity) + (g / count) * intensity);
                    buf.data[offset + 2] = static_cast<uint8_t>(
                        temp[offset + 2] * (1 - intensity) + (b / count) * intensity);
                }
            }
        }
    }

    void apply_eye_enhancement(std::shared_ptr<Frame> frame,
                               const FaceInfo& face) {
        // GPU shader-based eye brightening
        // In production: use face landmarks to mask eye region,
        // apply unsharp masking with eye_mask from beauty.glsl
    }

    void apply_lip_enhancement(std::shared_ptr<Frame> frame,
                               const FaceInfo& face) {
        // GPU shader-based lip color enhancement
        // In production: use face landmarks for lip mask,
        // increase saturation and apply tint
    }

    void apply_face_slimming(std::shared_ptr<Frame> frame,
                             const FaceInfo& face) {
        // GPU mesh-based face warping
        // In production:
        // 1. Build face mesh from landmarks
        // 2. Warp texture coordinates toward center
        // 3. Apply displacement in shader
    }

    void apply_3d_lut(std::shared_ptr<Frame> frame) {
        // GPU shader-based 3D LUT
        // In production: bind LUT texture, apply 3d_lut.glsl
    }

    void shutdown() {
        if (face_detector_) face_detector_->shutdown();
        if (face_landmark_) face_landmark_->shutdown();
    }
};

BeautyPipeline::BeautyPipeline() : impl_(std::make_unique<Impl>()) {}
BeautyPipeline::~BeautyPipeline() = default;

bool BeautyPipeline::init(const BeautyConfig& config, AIBackend backend) {
    return impl_->init(config, backend);
}

bool BeautyPipeline::process(std::shared_ptr<Frame> frame,
                              const FaceInfo* face_info) {
    return impl_->process_frame(frame, face_info);
}

void BeautyPipeline::shutdown() { impl_->shutdown(); }
void BeautyPipeline::set_config(const BeautyConfig& config) { impl_->config_ = config; }
