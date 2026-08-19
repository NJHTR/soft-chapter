add_library(streaming_engine_gpu
    gpu_pipeline.cpp
    shader_manager.cpp
)

target_include_directories(streaming_engine_gpu PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(streaming_engine_gpu
    PUBLIC
    streaming_engine_common
    ${OPENGL_LIBRARIES}
)

if(ENABLE_CUDA AND CUDA_FOUND)
    target_sources(streaming_engine_gpu PRIVATE
        cuda/cuda_pipeline.cpp
        cuda/cuda_oversample.cu
    )
    target_include_directories(streaming_engine_gpu PRIVATE ${CUDA_INCLUDE_DIRS})
    target_link_libraries(streaming_engine_gpu PRIVATE ${CUDA_LIBRARIES})
    target_compile_definitions(streaming_engine_gpu PRIVATE ENABLE_CUDA)
endif()

if(ENABLE_VULKAN AND Vulkan_FOUND)
    target_sources(streaming_engine_gpu PRIVATE
        vulkan/vk_pipeline.cpp
    )
    target_link_libraries(streaming_engine_gpu PRIVATE ${Vulkan_LIBRARIES})
    target_compile_definitions(streaming_engine_gpu PRIVATE ENABLE_VULKAN)
endif()
