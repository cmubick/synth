# Builds the sound engine as a library. iOS compiles these files directly in
# Xcode; this file is for Android (NDK) and desktop testing.
cmake_minimum_required(VERSION 3.18)
project(synth_engine CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(synth_engine STATIC
    src/SynthEngine.cpp
    src/Params.cpp
    src/CApi.cpp
)
target_include_directories(synth_engine PUBLIC include PRIVATE src)
