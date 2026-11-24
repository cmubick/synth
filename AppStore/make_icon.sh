#!/bin/sh
# Remakes the app icon from AppStore/make_icon.swift. Run from anywhere.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILD="$ROOT/AppStore/.build"
mkdir -p "$BUILD"
swiftc -parse-as-library -O "$ROOT/AppStore/make_icon.swift" "$ROOT/iOS/Synth/UI/PixelArt.swift" -o "$BUILD/make_icon"
cd "$ROOT" && "$BUILD/make_icon"
