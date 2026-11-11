# Pysanka: eSpeak NG for the English voice's phonemes: built from source at a fixed release (see
# ../../../prebuilt/VERSION and .github/workflows/espeak.yml), linked statically; only the two JNI
# calls are exported.
cmake_minimum_required(VERSION 3.22)
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)
project(pysanka_speech C)

set(ESPEAK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../prebuilt)
file(GLOB ESPEAK_LIBS ${ESPEAK_DIR}/lib/${ANDROID_ABI}/*.a)
add_library(pysanka_speech SHARED espeak_jni.c)
target_include_directories(pysanka_speech PRIVATE ${ESPEAK_DIR}/include)
target_compile_options(pysanka_speech PRIVATE -Wall -Wextra -Werror -O2 -fstack-protector-strong -D_FORTIFY_SOURCE=2)
target_link_options(pysanka_speech PRIVATE -Wl,-z,relro,-z,now -Wl,-z,max-page-size=16384 -Wl,--exclude-libs,ALL)
# (the libraries in a group: eSpeak's own parts refer to each other)
target_link_libraries(pysanka_speech -Wl,--start-group ${ESPEAK_LIBS} -Wl,--end-group log)
