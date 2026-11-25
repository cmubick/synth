# Builds libsynth.so: the shared engine from ../Engine (exactly the code the iOS app
# compiles), the Android audio output (AAudio) and the JNI functions Kotlin calls.
cmake_minimum_required(VERSION 3.22)
project(synth CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# The repository's Engine folder, five levels up from here (android/app/src/main/cpp).
set(ENGINE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../Engine)
add_subdirectory(${ENGINE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/engine)

add_library(synth SHARED
    AudioOutput.cpp
    JniBridge.cpp
)

target_include_directories(synth PRIVATE ${ENGINE_DIR}/include)
# Lets AudioOutput.cpp use newer AAudio calls behind __builtin_available on newer
# phones while still running on Android 8 (minSdk 26).
target_compile_definitions(synth PRIVATE __ANDROID_UNAVAILABLE_SYMBOLS_ARE_WEAK__)
# The engine is a static library; Android needs it compiled as position-independent.
set_target_properties(synth_engine PROPERTIES POSITION_INDEPENDENT_CODE ON)

find_library(log-lib log)
find_library(aaudio-lib aaudio)
target_link_libraries(synth synth_engine ${aaudio-lib} ${log-lib})
