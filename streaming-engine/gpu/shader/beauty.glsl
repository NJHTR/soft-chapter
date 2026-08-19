#version 460 core

// TikTok-style Beauty Filter GPU Shader
// Skin smoothing, eye enhancement, color correction

layout(binding = 0) uniform sampler2D inputTexture;
layout(binding = 1) uniform sampler2D faceMaskTexture; // Face mesh mask

layout(location = 0) out vec4 fragColor;

uniform float skinSmoothness = 0.4;   // [0, 1]
uniform float eyeBrightness = 0.3;    // [0, 1]
uniform float lipSaturation = 0.2;    // [0, 1]
uniform float faceSlim = 0.0;         // [-1, 1]
uniform float skinTone = 0.0;         // [-1, 1]
uniform vec2 faceCenter;              // Normalized face center

in vec2 uv;

// Bilateral filter for skin smoothing (edge-preserving)
vec4 bilateralFilter(vec2 coord, float sigmaS, float sigmaR) {
    vec2 texelSize = 1.0 / textureSize(inputTexture, 0);
    vec4 center = texture(inputTexture, coord);

    vec4 sum = vec4(0.0);
    float totalWeight = 0.0;
    int radius = int(ceil(sigmaS * 2.0));

    for (int y = -radius; y <= radius; y++) {
        for (int x = -radius; x <= radius; x++) {
            vec2 sampleCoord = coord + vec2(x, y) * texelSize;
            vec4 sampleColor = texture(inputTexture, sampleCoord);

            float spatialDist = float(x * x + y * y) / (2.0 * sigmaS * sigmaS);
            float colorDist = dot(sampleColor.rgb - center.rgb,
                                  sampleColor.rgb - center.rgb) / (2.0 * sigmaR * sigmaR);
            float weight = exp(-spatialDist - colorDist);

            sum += sampleColor * weight;
            totalWeight += weight;
        }
    }
    return sum / totalWeight;
}

// Skin color detection (YCrCb based)
float skinMask(vec3 color) {
    const vec3 ycrcb = color * mat3(
        0.299, -0.1687, 0.5,
        0.587, -0.3313, -0.4187,
        0.114, 0.5, -0.0813
    );
    float cr = ycrcb.g + 0.5;
    float cb = ycrcb.b + 0.5;
    return smoothstep(0.0, 1.0, 1.0 - abs(cr - 0.5) * 4.0) *
           smoothstep(0.0, 1.0, 1.0 - abs(cb - 0.5) * 4.0);
}

// Eye region enhancement (sharpening + brightening)
vec4 enhanceEyes(vec4 color, vec2 coord, float eyeMap) {
    vec4 enhanced = color;
    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Increase local contrast in eye area
    float sharp = lum - (lum * 0.8 + 0.1);
    enhanced.rgb += sharp * eyeBrightness * eyeMap * 0.3;

    // Brighten iris
    enhanced.rgb += vec3(0.05, 0.03, 0.0) * eyeBrightness * eyeMap;
    return enhanced;
}

void main() {
    vec4 color = texture(inputTexture, uv);
    float skin = skinMask(color.rgb);

    // Apply bilateral filter on skin areas
    vec4 smoothColor = bilateralFilter(uv, 2.0, 0.1);
    vec4 outputColor = mix(color, smoothColor, skin * skinSmoothness);

    // Read face mask from texture
    vec4 faceMask = texture(faceMaskTexture, uv);
    float eyeMask = faceMask.r;   // Red channel = eye mask
    float lipMask = faceMask.g;   // Green channel = lip mask

    // Eye enhancement
    outputColor = enhanceEyes(outputColor, uv, eyeMask);

    // Lip color enhancement
    if (lipMask > 0.1) {
        vec3 lipColor = vec3(0.9, 0.3, 0.3);
        outputColor.rgb = mix(outputColor.rgb, lipColor, lipMask * lipSaturation * 0.3);
    }

    // Overall color grading (warm tone)
    outputColor.rgb *= vec3(1.02, 1.0, 0.98);

    fragColor = outputColor;
}
