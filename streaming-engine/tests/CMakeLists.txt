add_executable(streaming_engine_tests
    pipeline_test.cpp
)

target_link_libraries(streaming_engine_tests
    PRIVATE
    streaming_engine_capture
    streaming_engine_gpu
    streaming_engine_encoder
    streaming_engine_network
    streaming_engine_common
)

add_executable(native_camera_test
    native_camera_test.cpp
)

target_include_directories(native_camera_test
    PRIVATE
    ${CMAKE_SOURCE_DIR}
    ${FFMPEG_INCLUDE_DIRS}
)

target_link_libraries(native_camera_test
    PRIVATE
    streaming_engine_capture
    streaming_engine_gpu
    streaming_engine_encoder
    streaming_engine_common
    ${FFMPEG_LIBRARIES}
)

if(WIN32)
    target_link_libraries(native_camera_test PRIVATE
        mfplat.lib mf.lib mfuuid.lib mfreadwrite.lib ole32.lib oleaut32.lib
    )
endif()

if(ENABLE_TESTS)
    add_test(NAME streaming_engine_tests
             COMMAND streaming_engine_tests)
    add_test(NAME native_camera_test
             COMMAND native_camera_test)
endif()
