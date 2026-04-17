cmake_minimum_required(VERSION 3.22.1)
project(baml_jni C)

# ---- Pre-built Rust library (libbridge_cffi.so) ----
# The user must place the cross-compiled .so files at:
#   app/src/main/jniLibs/arm64-v8a/libbridge_cffi.so
#   app/src/main/jniLibs/x86_64/libbridge_cffi.so
#
# CMake discovers them as an IMPORTED SHARED library so that
# libbaml_jni.so can link against the symbols.

add_library(bridge_cffi SHARED IMPORTED)
set_target_properties(bridge_cffi PROPERTIES
    IMPORTED_LOCATION ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libbridge_cffi.so
)

# ---- JNI bridge library ----
add_library(baml_jni SHARED baml_jni.c)

target_link_libraries(baml_jni
    bridge_cffi
    log        # Android __android_log_print (optional, for debugging)
)
