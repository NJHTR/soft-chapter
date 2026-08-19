#include "common/pipeline.h"
#include <iostream>

VideoPipeline::VideoPipeline() = default;
VideoPipeline::~VideoPipeline() = default;

bool VideoPipeline::init(const PipelineConfig& config) {
    config_ = config;
    running_ = true;
    std::cout << "Pipeline initialized: "
              << config_.input_width << "x" << config_.input_height
              << " → " << config_.output_width << "x" << config_.output_height
              << " @ " << config_.fps << "fps"
              << " [" << config_.encoder_codec << "@" << config_.bitrate << "bps]"
              << std::endl;
    return true;
}

bool VideoPipeline::push_frame(std::shared_ptr<Frame> frame) {
    if (!running_) return false;

    // Run through each pipeline node
    auto current_frame = frame;

    for (auto& node : nodes_) {
        std::shared_ptr<Frame> output;
        if (!node->process(current_frame, output)) {
            std::cerr << "Pipeline node " << node->name() << " failed" << std::endl;
            return false;
        }
        current_frame = output;
    }

    // Call output callback
    if (output_cb_) {
        output_cb_(current_frame);
    }

    return true;
}

void VideoPipeline::shutdown() {
    running_ = false;
    for (auto& node : nodes_) {
        node->shutdown();
    }
    nodes_.clear();
}
