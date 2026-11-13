cmake_minimum_required(VERSION 3.22.1)

project(whisper-jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_FFMPEG OFF CACHE BOOL "" FORCE)

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

set(WHISPER_DIR
    "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp"
)

if(NOT EXISTS "${WHISPER_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "Folder third_party/whisper.cpp tidak ditemukan!"
    )
endif()

add_subdirectory(
    "${WHISPER_DIR}"
    "${CMAKE_CURRENT_BINARY_DIR}/whisper"
)

add_library(
    whisper-jni
    SHARED
    whisper_jni.cpp
)

find_library(
    android-log
    log
)

target_link_libraries(
    whisper-jni
    PRIVATE
    whisper
    ${android-log}
)
