cmake_minimum_required(VERSION 3.22.1)
project("astra_llama")

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(LLAMA_BUILD_COMMON   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP          OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE          OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS    OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp)

add_library(astra_llama SHARED llama_jni.cpp)
target_link_libraries(astra_llama llama android log)
