# Pysanka: whisper.cpp (MIT, vendored in ./whisper.cpp, see SOURCE.md) for dictation on the phone.
# Made after whisper.cpp's own Android example: ggml built by its own CMake (CPU only, no OpenMP,
# no other backends), whisper.cpp and a small JNI layer in one shared library.
cmake_minimum_required(VERSION 3.22)
project(pysanka_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

# ggml: CPU only, built for ARMv8.2 with half floats and dot product (checked on the phone before
# the library is loaded, see WhisperNative.supported)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
add_subdirectory(${WHISPER_DIR}/ggml ggml)

add_library(pysanka_whisper SHARED
    ${WHISPER_DIR}/src/whisper.cpp
    ${WHISPER_DIR}/src/aneforge/whisper-aneforge.cpp
    jni.cpp
)
target_include_directories(pysanka_whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include
)
target_compile_definitions(pysanka_whisper PRIVATE GGML_USE_CPU WHISPER_VERSION="1.9.5")
target_compile_options(pysanka_whisper PRIVATE -O3 -fvisibility=hidden -fvisibility-inlines-hidden
    -ffunction-sections -fdata-sections)
target_link_options(pysanka_whisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL
    # 16 KB pages (Android 15+)
    -Wl,-z,max-page-size=16384)
find_library(LOG_LIB log)
target_link_libraries(pysanka_whisper ggml ${LOG_LIB})
