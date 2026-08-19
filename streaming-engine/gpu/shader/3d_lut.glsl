#version 460 core

// 3D LUT Color Grading
// Supports: ACES, Film LUTs (Kodak, Fuji), Cinematic, Custom LUTs

layout(binding = 0) uniform sampler2D inputTexture;
layout(binding = 1) uniform sampler3D lutTexture;  // 3D LUT (64x64x64 or 33x33x33)

layout(location = 0) out vec4 fragColor;

uniform float lutIntensity = 1.0;   // [0, 1] blend with original
uniform int lutSize = 64;           // 33 or 64

in vec2 uv;

vec3 apply3DLUT(vec3 color) {
    // Scale input to LUT coordinates
    vec3 lutCoord = clamp(color, 0.0, 1.0);

    // Calculate slice and fractional position
    float sliceSize = 1.0 / float(lutSize);
    vec3 lutUV = lutCoord * (float(lutSize) - 1.0) / float(lutSize) + 0.5 / float(lutSize);

    // Trilinear interpolation
    vec3 lutResult = texture(lutTexture, lutUV).rgb;

    return lutResult;
}

void main() {
    vec4 color = texture(inputTexture, uv);

    if (lutIntensity > 0.0) {
        vec3 graded = apply3DLUT(color.rgb);
        color.rgb = mix(color.rgb, graded, lutIntensity);
    }

    fragColor = color;
}
