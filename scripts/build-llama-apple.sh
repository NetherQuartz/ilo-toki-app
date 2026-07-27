#!/usr/bin/env bash
#
# Builds llama.cpp together with the shared C core (llm/src/nativeCommon/cpp) into
# one static archive per iOS target, ready for :llm's cinterop.
#
# Each target gets a single merged libilotoki_llm.a so the cinterop definition can
# stay target-independent. Device builds use Metal; the simulator falls back to CPU
# because ggml's Metal backend is not usable there.
#
# Usage: scripts/build-llama-apple.sh [output-root]
#        scripts/build-llama-apple.sh --clean

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LLAMA_DIR="$ROOT/llama.cpp"
CORE_DIR="$ROOT/llm/src/nativeCommon/cpp"

IOS_MIN_OS_VERSION=16.4

if [[ "${1:-}" == "--clean" ]]; then
    rm -rf "$ROOT/llm/build/llama-apple"
    echo "Removed $ROOT/llm/build/llama-apple"
    exit 0
fi

OUT_ROOT="${1:-$ROOT/llm/build/llama-apple}"

if [[ ! -f "$LLAMA_DIR/CMakeLists.txt" ]]; then
    echo "error: llama.cpp submodule is missing. Run: git submodule update --init --recursive" >&2
    exit 1
fi

JOBS="$(sysctl -n hw.ncpu)"

# $1 target name, $2 sdk (iphoneos|iphonesimulator), $3 GGML_METAL (ON|OFF),
# $4 clang version-min flag
build_target() {
    local name="$1" sdk="$2" metal="$3" min_flag="$4"
    local build_dir="$OUT_ROOT/$name/build"
    local lib_dir="$OUT_ROOT/$name/lib"
    local out_lib="$lib_dir/libilotoki_llm.a"

    # The merged archive is the only artifact consumed downstream; rebuild it when
    # it is older than either the core sources or the checked-out llama.cpp.
    if [[ -f "$out_lib" \
        && "$out_lib" -nt "$CORE_DIR/ilotoki_llm.cpp" \
        && "$out_lib" -nt "$CORE_DIR/ilotoki_llm.h" \
        && "$out_lib" -nt "$LLAMA_DIR/CMakeLists.txt" ]]; then
        echo "==> $name is up to date ($out_lib)"
        return
    fi

    echo "==> Configuring llama.cpp for $name (sdk=$sdk, metal=$metal)"
    mkdir -p "$build_dir" "$lib_dir"
    cmake -S "$LLAMA_DIR" -B "$build_dir" \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_SYSTEM_NAME=iOS \
        -DCMAKE_OSX_SYSROOT="$sdk" \
        -DCMAKE_OSX_ARCHITECTURES=arm64 \
        -DCMAKE_OSX_DEPLOYMENT_TARGET="$IOS_MIN_OS_VERSION" \
        -DCMAKE_C_FLAGS="-Wno-shorten-64-to-32 -Wno-unused-command-line-argument" \
        -DCMAKE_CXX_FLAGS="-Wno-shorten-64-to-32 -Wno-unused-command-line-argument" \
        -DBUILD_SHARED_LIBS=OFF \
        -DLLAMA_BUILD_COMMON=OFF \
        -DLLAMA_BUILD_EXAMPLES=OFF \
        -DLLAMA_BUILD_TOOLS=OFF \
        -DLLAMA_BUILD_TESTS=OFF \
        -DLLAMA_BUILD_SERVER=OFF \
        -DLLAMA_BUILD_APP=OFF \
        -DLLAMA_BUILD_UI=OFF \
        -DLLAMA_CURL=OFF \
        -DGGML_NATIVE=OFF \
        -DGGML_OPENMP=OFF \
        -DGGML_ACCELERATE=ON \
        -DGGML_BLAS=ON \
        -DGGML_BLAS_VENDOR=Apple \
        -DGGML_METAL="$metal" \
        -DGGML_METAL_EMBED_LIBRARY="$metal" \
        > "$build_dir.configure.log" 2>&1 || { cat "$build_dir.configure.log" >&2; exit 1; }

    echo "==> Building llama.cpp for $name"
    cmake --build "$build_dir" --config Release -j"$JOBS" \
        > "$build_dir.build.log" 2>&1 || { tail -50 "$build_dir.build.log" >&2; exit 1; }

    echo "==> Compiling the shared C core for $name"
    xcrun --sdk "$sdk" clang++ -c "$CORE_DIR/ilotoki_llm.cpp" -o "$build_dir/ilotoki_llm.o" \
        -std=c++17 -O3 -fvisibility=hidden -fvisibility-inlines-hidden \
        -arch arm64 "$min_flag" \
        -isysroot "$(xcrun --sdk "$sdk" --show-sdk-path)" \
        -I"$CORE_DIR" -I"$LLAMA_DIR/include" -I"$LLAMA_DIR/ggml/include"

    echo "==> Merging static archives for $name"
    mkdir -p "$lib_dir"
    rm -f "$out_lib"
    # shellcheck disable=SC2046
    xcrun libtool -static -o "$out_lib" \
        "$build_dir/ilotoki_llm.o" \
        $(find "$build_dir" -name '*.a' -print) 2>&1 | grep -v 'has no symbols' || true

    if [[ ! -f "$out_lib" ]]; then
        echo "error: failed to produce $out_lib" >&2
        exit 1
    fi
    echo "==> $name: $(du -h "$out_lib" | cut -f1) -> $out_lib"
}

build_target "ios-device"    "iphoneos"        "ON"  "-mios-version-min=$IOS_MIN_OS_VERSION"
build_target "ios-simulator" "iphonesimulator" "OFF" "-mios-simulator-version-min=$IOS_MIN_OS_VERSION"

echo "Done."
