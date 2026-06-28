# Sub Rosa — on-device whisper.cpp (CPU, arm64-v8a). Builds a single libwhisper.so containing
# whisper.cpp + the JNI bridge, linking ggml (its own CMake handles the refactored ggml file set,
# so we never hand-list ggml sources — robust across upstream refactors).
cmake_minimum_required(VERSION 3.18)
project(whisper_android C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android Gradle compiles the *debug* variant's native code with CMAKE_BUILD_TYPE=Debug, i.e. -O0.
# That leaves ggml's matmul kernels unoptimized and makes whisper ~50-100x slower (a 4 s clip took
# ~19 s, rtf 0.2x). Force -O3 for the Debug config too so the debug APK still ships optimized CPU
# kernels for ASR. (RelWithDebInfo/Release already optimize; this only lifts Debug.)
set(CMAKE_C_FLAGS_DEBUG   "-O3 -DNDEBUG" CACHE STRING "" FORCE)
set(CMAKE_CXX_FLAGS_DEBUG "-O3 -DNDEBUG" CACHE STRING "" FORCE)

set(WHISPER_LIB_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# CPU-only Android config: no OpenMP toolchain in the NDK; never -march=native when cross-compiling;
# single static CPU backend (no dynamic-load registry); static libs folded into our one .so.
set(GGML_OPENMP       OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE    OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE       OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL   OFF CACHE BOOL "" FORCE)
set(GGML_CPU          ON  CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

# ggml's own CMake (defines targets ggml / ggml-base / ggml-cpu, incl. ARM arch kernels).
add_subdirectory(${WHISPER_LIB_DIR}/ggml ${CMAKE_BINARY_DIR}/ggml_build)

find_library(LOG_LIB log)

add_library(whisper SHARED
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/jni.c)

target_compile_definitions(whisper PUBLIC GGML_USE_CPU)
target_compile_definitions(whisper PRIVATE WHISPER_VERSION="1.9.1")
target_include_directories(whisper PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src)

target_compile_options(whisper PRIVATE -O3 -fvisibility=hidden)
# Android 15 16 KB page-size support: align LOAD segments to 16 KB (NDK r27 doesn't default to this).
target_link_options(whisper PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
target_link_libraries(whisper ${LOG_LIB} android ggml)
