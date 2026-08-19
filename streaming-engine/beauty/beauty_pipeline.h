#pragma once

#include "common/frame.h"
#include "ai/ai_engine.h"
#include <memory>
#include <vector>

// TikTok-style Beauty Pipeline
// All processing runs on GPU with zero CPU copy

struct BeautyConfig {
    bool enable_skin_smooth = true;
    bool enable_eye_enhance = true;
    bool enable_lip_enhance = true;
    bool enable_face_slim = true;
    bool enable_jaw_adjust = false;
    bool enable_nose_adjust = false;
    bool enable_color_lut = true;

    // Intensity [0, 1]
    float skin_smoothness = 0.4f;
    float eye_brightness = 0.3f;
    float lip_saturation = 0.2f;
    float face_slim_amount = 0.0f;
    float jaw_width = 0.0f;    // [-1, 1]
    float nose_size = 0.0f;    // [-1, 1]

    std::string lut_path = "models/lut/natural.cube";
};

struct FaceInfo {
    float face_rect[4];        // x, y, w, n (normalized)
    float landmarks[468][2];   // MediaPipe 468 face landmarks
    float face_mesh_uv[478][2];
    int num_faces = 0;
    float gaze_vector[3];
    float head_rotation[3];    // pitch, yaw, roll
};

// Face landmark key indices
enum FaceLandmark {
    LEFT_EYE_INNER  = 133,
    LEFT_EYE_OUTER  = 33,
    RIGHT_EYE_INNER = 362,
    RIGHT_EYE_OUTER = 263,
    NOSE_TIP        = 1,
    LIPS_UPPER      = 13,
    LIPS_LOWER      = 14,
    JAW_LEFT        = 172,
    JAW_RIGHT       = 397,
    CHIN            = 152,
};

class BeautyPipeline {
public:
    BeautyPipeline();
    ~BeautyPipeline();

    bool init(const BeautyConfig& config,
              AIBackend face_backend = AIBackend::TensorRT);
    bool process(std::shared_ptr<Frame> frame,
                 const FaceInfo* face_info = nullptr);
    void shutdown();

    void set_config(const BeautyConfig& config);
    const BeautyConfig& config() const { return config_; }

    // GPU output for compositing
    std::shared_ptr<Frame> output() const { return output_; }

private:
    class Impl;
    std::unique_ptr<Impl> impl_;
    BeautyConfig config_;
    std::shared_ptr<Frame> output_;

    bool detect_faces(std::shared_ptr<Frame> frame,
                      std::vector<FaceInfo>& faces);
    void apply_skin_smoothing(std::shared_ptr<Frame> frame,
                              const FaceInfo& face);
    void apply_eye_enhancement(std::shared_ptr<Frame> frame,
                               const FaceInfo& face);
    void apply_lip_enhancement(std::shared_ptr<Frame> frame,
                               const FaceInfo& face);
    void apply_face_slimming(std::shared_ptr<Frame> frame,
                             const FaceInfo& face);
    void apply_3d_lut(std::shared_ptr<Frame> frame);
};
