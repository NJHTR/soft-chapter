#version 460 core

// HDR Tone Mapping & Color Space Conversion
// Supports: BT.709, BT.2020, HDR10 (ST.2084 PQ), HLG, SDR

layout(binding = 0) uniform sampler2D inputTexture;
layout(location = 0) out vec4 fragColor;

uniform int colorSpace = 0;  // 0=BT709, 1=BT2020, 2=HDR10
uniform float exposure = 1.0;
uniform float gamma = 2.2;

in vec2 uv;

// BT.709 → Linear
vec3 bt709ToLinear(vec3 c) {
    return pow(c, vec3(gamma));
}

// Linear → BT.709 (with gamma)
vec3 linearToBt709(vec3 c) {
    return pow(c, vec3(1.0 / gamma));
}

// Linear → BT.2020 PQ (HDR10)
vec3 linearToPQ(vec3 c) {
    const float m1 = 0.1593017578125;
    const float m2 = 78.84375;
    const float c1 = 0.8359375;
    const float c2 = 18.8515625;
    const float c3 = 18.6875;

    vec3 n = pow(c * exposure, vec3(m1));
    return pow((c1 + c2 * n) / (1.0 + c3 * n), vec3(m2));
}

// Linear → HLG
vec3 linearToHLG(vec3 c) {
    const float a = 0.17883277;
    const float b = 0.28466892;
    const float c = 0.55991073;

    vec3 hlg;
    for (int i = 0; i < 3; i++) {
        if (c[i] <= 1.0 / 12.0) {
            hlg[i] = sqrt(3.0 * c[i]);
        } else {
            hlg[i] = a * log(12.0 * c[i] - b) + c;
        }
    }
    return hlg;
}

// ACES filmic tone mapping
vec3 acesFilmic(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec4 color = texture(inputTexture, uv);
    vec3 linear = bt709ToLinear(color.rgb) * exposure;
    vec3 outputColor;

    switch (colorSpace) {
        case 0: // BT.709 SDR
            outputColor = acesFilmic(linear);
            outputColor = linearToBt709(outputColor);
            break;

        case 1: // BT.2020
            outputColor = linearToBt709(acesFilmic(linear));
            break;

        case 2: // HDR10 PQ
            outputColor = linearToPQ(linear);
            break;

        case 3: // HLG
            outputColor = linearToHLG(linear);
            break;

        default:
            outputColor = linearToBt709(acesFilmic(linear));
    }

    fragColor = vec4(outputColor, color.a);
}
