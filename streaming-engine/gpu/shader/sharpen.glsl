#version 460 core

// AI-aware sharpen: Edge-aware unsharp mask with guided filter
// Prevents haloing and noise amplification

layout(binding = 0) uniform sampler2D inputTexture;
layout(location = 0) out vec4 fragColor;

uniform float sharpness = 0.3;    // [0, 1]
uniform float edgeThreshold = 0.1;
uniform float noiseGate = 0.02;   // Prevents noise amplification

in vec2 uv;

vec4 guidedFilter(vec2 coord, float r, float eps) {
    vec2 texelSize = 1.0 / textureSize(inputTexture, 0);
    vec4 I = texture(inputTexture, coord);

    // Mean of I and p over window
    vec4 meanI = vec4(0.0);
    vec4 meanP = vec4(0.0);
    int radius = int(ceil(r));
    int count = 0;

    for (int y = -radius; y <= radius; y++) {
        for (int x = -radius; x <= radius; x++) {
            vec2 sampleCoord = coord + vec2(x, y) * texelSize;
            vec4 sampleColor = texture(inputTexture, sampleCoord);
            meanI += sampleColor;
            meanP += sampleColor;
            count++;
        }
    }
    meanI /= float(count);
    meanP /= float(count);

    // Covariance and variance
    vec4 corrI = vec4(0.0);
    vec4 corrIp = vec4(0.0);
    for (int y = -radius; y <= radius; y++) {
        for (int x = -radius; x <= radius; x++) {
            vec2 sampleCoord = coord + vec2(x, y) * texelSize;
            vec4 sampleColor = texture(inputTexture, sampleCoord);
            corrI += sampleColor * sampleColor;
            corrIp += sampleColor * sampleColor;
        }
    }
    corrI /= float(count);
    corrIp /= float(count);

    vec4 varI = corrI - meanI * meanI;
    vec4 covIp = corrIp - meanI * meanP;

    // Linear coefficients
    vec4 a = covIp / (varI + eps);
    vec4 b = meanP - a * meanI;

    // Output
    return a * I + b;
}

void main() {
    vec4 color = texture(inputTexture, uv);

    // Convert to luminance for edge detection
    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Compute local variance for edge detection
    vec2 texelSize = 1.0 / textureSize(inputTexture, 0);
    float localVar = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 sampleCoord = uv + vec2(x, y) * texelSize;
            float s = dot(texture(inputTexture, sampleCoord).rgb, vec3(0.299, 0.587, 0.114));
            localVar += (s - lum) * (s - lum);
        }
    }
    localVar /= 9.0;

    // Noise gate: skip smoothing for low-variance (flat) areas
    float noiseWeight = smoothstep(0.0, noiseGate, localVar);

    // Edge-aware unsharp mask
    vec4 smooth = guidedFilter(uv, 2.0, 0.01);
    vec4 detail = color - smooth;

    // Edge threshold to prevent haloing
    float edgeWeight = smoothstep(edgeThreshold, edgeThreshold * 2.0, localVar);
    float finalWeight = sharpness * edgeWeight * noiseWeight;

    vec4 outputColor = color + detail * finalWeight;

    fragColor = outputColor;
}
