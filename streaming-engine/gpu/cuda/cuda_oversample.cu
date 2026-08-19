// CUDA kernel: Lanczos3 downsampling for 4K → 1080P
// Texture-based oversampling with sub-pixel accuracy

texture<float4, cudaTextureType2D, cudaReadModeElementType> tex_input;

__device__ float lanczos_weight(float x, float a) {
    if (x == 0.0f) return 1.0f;
    if (x >= a) return 0.0f;
    float pix = M_PI * x;
    return a * sin(pix) * sin(pix / a) / (pix * pix);
}

__global__ void oversample_lanczos3_kernel(
    float4* output, int out_w, int out_h,
    int in_w, int in_h, float scale_x, float scale_y) {

    int ox = blockIdx.x * blockDim.x + threadIdx.x;
    int oy = blockIdx.y * blockDim.y + threadIdx.y;

    if (ox >= out_w || oy >= out_h) return;

    float cx = (ox + 0.5f) / scale_x - 0.5f;
    float cy = (oy + 0.5f) / scale_y - 0.5f;

    const float a = 3.0f; // Lanczos radius
    float4 result = make_float4(0, 0, 0, 0);
    float total_weight = 0.0f;

    int ix_start = max(0, (int)floor(cx - a + 1));
    int iy_start = max(0, (int)floor(cy - a + 1));
    int ix_end = min(in_w - 1, (int)floor(cx + a));
    int iy_end = min(in_h - 1, (int)floor(cy + a));

    for (int iy = iy_start; iy <= iy_end; ++iy) {
        for (int ix = ix_start; ix <= ix_end; ++ix) {
            float wx = lanczos_weight((ix - cx), a);
            float wy = lanczos_weight((iy - cy), a);
            float w = wx * wy;
            float4 pixel = tex2D(tex_input, ix + 0.5f, iy + 0.5f);
            result.x += pixel.x * w;
            result.y += pixel.y * w;
            result.z += pixel.z * w;
            result.w += pixel.w * w;
            total_weight += w;
        }
    }

    if (total_weight > 0.0f) {
        float inv = 1.0f / total_weight;
        output[oy * out_w + ox] = make_float4(
            result.x * inv, result.y * inv,
            result.z * inv, result.w * inv);
    }
}

// Host function to launch the oversampling kernel
extern "C" void launch_oversample_lanczos3(
    cudaArray_t input_arr, int in_w, int in_h,
    float4* output_d, int out_w, int out_h,
    cudaStream_t stream) {

    // Bind texture to input array
    tex_input.normalized = false;
    tex_input.filterMode = cudaFilterModeLinear;
    tex_input.addressMode[0] = cudaAddressModeClamp;
    tex_input.addressMode[1] = cudaAddressModeClamp;
    cudaBindTextureToArray(&tex_input, input_arr);

    float scale_x = (float)out_w / (float)in_w;
    float scale_y = (float)out_h / (float)in_h;

    dim3 block(16, 16);
    dim3 grid((out_w + 15) / 16, (out_h + 15) / 16);

    oversample_lanczos3_kernel<<<grid, block, 0, stream>>>(
        output_d, out_w, out_h, in_w, in_h, scale_x, scale_y);

    cudaUnbindTexture(&tex_input);
}
