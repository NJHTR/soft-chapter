add_library(streaming_engine_capture
    capture_factory.cpp
    camera/camera_capture.cpp
    ffmpeg/ffmpeg_capture.cpp
)

target_include_directories(streaming_engine_capture
    PUBLIC
    ${CMAKE_SOURCE_DIR}
    ${FFMPEG_INCLUDE_DIRS}
)
target_link_libraries(streaming_engine_capture
    PUBLIC
    streaming_engine_common
    ${FFMPEG_LIBRARIES}
    opencv_imgproc
)

if(WIN32)
    target_link_libraries(streaming_engine_capture PUBLIC
        mfplat.lib mf.lib mfuuid.lib mfreadwrite.lib ole32.lib oleaut32.lib
    )
    target_compile_definitions(streaming_engine_capture PRIVATE ENABLE_MF)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/mediafoundation/mf_capture.cpp")
        target_sources(streaming_engine_capture PRIVATE
            mediafoundation/mf_capture.cpp
        )
    endif()
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/directshow/dshow_capture.cpp")
        target_sources(streaming_engine_capture PRIVATE
            directshow/dshow_capture.cpp
        )
    endif()
endif()

if(APPLE)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/avfoundation/avf_capture.cpp")
        target_sources(streaming_engine_capture PRIVATE
            avfoundation/avf_capture.cpp
        )
    endif()
endif()

if(UNIX AND NOT APPLE)
    if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/v4l2/v4l2_capture.cpp")
        target_sources(streaming_engine_capture PRIVATE
            v4l2/v4l2_capture.cpp
        )
    endif()
endif()
