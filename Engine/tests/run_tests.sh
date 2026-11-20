#!/bin/sh
# Builds and runs the engine test on this Mac. Usage: Engine/tests/run_tests.sh [output.wav]
set -e
ENGINE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BUILD_DIR="$ENGINE_DIR/build"
mkdir -p "$BUILD_DIR"
clang++ -std=c++17 -O2 -Wall -Wextra \
    -I"$ENGINE_DIR/include" \
    "$ENGINE_DIR"/src/*.cpp "$ENGINE_DIR/tests/engine_test.cpp" \
    -o "$BUILD_DIR/engine_test"
"$BUILD_DIR/engine_test" "${1:-$BUILD_DIR/engine_test.wav}"
