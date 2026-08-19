#pragma once

// JNI Bridge between Java Spring Boot server and native C++ streaming engine
// The Java server manages REST APIs, room state, WebSocket signaling
// The C++ engine handles the actual media pipeline

#ifdef __cplusplus
extern "C" {
#endif

// ===== Engine Lifecycle =====
JNIEXPORT jlong JNICALL
Java_com_douyin_engine_StreamingEngine_nativeCreate(JNIEnv* env, jobject thiz);

JNIEXPORT jboolean JNICALL
Java_com_douyin_engine_StreamingEngine_nativeInit(
    JNIEnv* env, jobject thiz, jlong engine_ptr, jobject config);

JNIEXPORT jboolean JNICALL
Java_com_douyin_engine_StreamingEngine_nativeStart(
    JNIEnv* env, jobject thiz, jlong engine_ptr);

JNIEXPORT void JNICALL
Java_com_douyin_engine_StreamingEngine_nativeStop(
    JNIEnv* env, jobject thiz, jlong engine_ptr);

JNIEXPORT void JNICALL
Java_com_douyin_engine_StreamingEngine_nativeDestroy(
    JNIEnv* env, jobject thiz, jlong engine_ptr);

// ===== Configuration =====
JNIEXPORT void JNICALL
Java_com_douyin_engine_StreamingEngine_nativeSetBitrate(
    JNIEnv* env, jobject thiz, jlong engine_ptr, jint bitrate);

JNIEXPORT void JNICALL
Java_com_douyin_engine_StreamingEngine_nativeSetBeautyConfig(
    JNIEnv* env, jobject thiz, jlong engine_ptr, jobject config);

// ===== Statistics =====
JNIEXPORT jobject JNICALL
Java_com_douyin_engine_StreamingEngine_nativeGetStats(
    JNIEnv* env, jobject thiz, jlong engine_ptr);

#ifdef __cplusplus
}
#endif
