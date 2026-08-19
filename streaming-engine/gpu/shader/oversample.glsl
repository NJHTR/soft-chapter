#version 460 core

// GPU Oversampling Shader: 4K → 1080P (OpenGL)
// Bicubic interpolation for high-quality downscale

layout(binding = 0) uniform sampler2D inputTexture;
layout(location = 0) out vec4 fragColor;

uniform vec2 inputSize;
uniform vec2 outputSize;
uniform float sharpness = 0.5; // [0,1] control

in vec2 uv;

// Mitchell-Netravali bicubic filter
vec4 bicubic(sampler2D tex, vec2 coord, vec2 texSize) {
    vec2 px = 1.0 / texSize;
    vec2 fc = fract(coord * texSize - 0.5);
    vec2 ic = floor(coord * texSize - 0.5);

    vec4 weights[4];
    for (int i = 0; i < 4; i++) {
        float x = abs(fc.x - (i - 1.5));
        float y = abs(fc.y - (i - 1.5));
        weights[i] = vec4(
            (1.0/6.0) * pow(max(0.0, 2.0 - x), 3.0) - (1.0/3.0) * pow(max(0.0, 1.0 - x), 3.0),
            (1.0/6.0) * pow(max(0.0, 2.0 - y), 3.0) - (1.0/3.0) * pow(max(0.0, 1.0 - y), 3.0),
            0.0, 0.0
        );
        weights[i].x = (weights[i].x + sharpness * (x - 2.0/3.0)) / (1.0 + sharpness/3.0);
        weights[i].y = (weights[i].y + sharpness * (y - 2.0/3.0)) / (1.0 + sharpness/3.0);
    }

    vec4 result = vec4(0.0);
    float sumW = 0.0, sumH = 0.0;

    for (int y = -1; y <= 2; y++) {
        for (int x = -1; x <= 2; x++) {
            vec2 samplePos = (ic + vec2(x, y) + 0.5) * px;
            vec4 color = texture(tex, samplePos);
            float w = weights[x+1].x * weights[y+1].y;
            result += color * w;
            sumW += w;
        }
    }
    return result / sumW;
}

void main() {
    vec4 color = bicubic(inputTexture, uv, inputSize);
    fragColor = color;
}
