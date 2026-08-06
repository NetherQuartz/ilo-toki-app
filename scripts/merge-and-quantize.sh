#!/usr/bin/env bash
#
# Turns a base model and a LoRA adapter into the GGUF files the app downloads.
#
#   scripts/merge-and-quantize.sh <base> <adapter> <name> [output-dir]
#
# <base> and <adapter> are either Hugging Face repo ids or local directories.
# <name> prefixes the GGUF files, e.g. `ilo-toki-MiLMMT-46-1b`.
#
# Everything lands in the output directory (default: build/models), ready to be
# uploaded with `hf upload <repo> <output-dir> .`.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LLAMA_DIR="$ROOT/llama.cpp"

if [[ $# -lt 3 ]]; then
    sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
    exit 2
fi

BASE="$1"
ADAPTER="$2"
NAME="$3"
OUT="${4:-$ROOT/build/models}"

# Quantizations the app offers. Q8_0 is the default it ships: on a model with a
# 262k-token vocabulary the embedding matrix dominates and quantizes the same way
# throughout, so Q8_0 costs little over Q4_K_M while giving up nothing.
QUANTS=(Q4_K_M Q5_K_M Q6_K Q8_0)

WORK="$OUT/.work"
mkdir -p "$WORK"

command -v uv >/dev/null || { echo "error: uv is required (brew install uv)" >&2; exit 1; }
[[ -f "$LLAMA_DIR/CMakeLists.txt" ]] || {
    echo "error: run: git submodule update --init --recursive" >&2; exit 1;
}

VENV="$WORK/venv"
if [[ ! -d "$VENV" ]]; then
    echo "==> creating the Python environment"
    uv venv --python 3.12 "$VENV"
    # The Xet transfer has a habit of wedging on multi-gigabyte files; the plain
    # HTTP path is slower but finishes.
    # Deliberately not llama.cpp's requirements file: it pins a torch nightly
    # index that will not resolve alongside a released transformers.
    VIRTUAL_ENV="$VENV" uv pip install --quiet \
        torch transformers peft safetensors sentencepiece protobuf numpy "huggingface_hub[cli]"
fi
# shellcheck disable=SC1091
source "$VENV/bin/activate"
export HF_HUB_DISABLE_XET=1

# Downloads <repo> unless it is already a directory on disk.
#
# The cache directory is named after the repo, not after the role it plays. Keying
# it on «adapter» instead silently reuses whatever was downloaded last time: the
# second model built on this machine came out byte-identical to the first, and the
# only sign was that the merge probe printed the same sentence.
resolve() {
    local spec="$1" role="$2"
    if [[ -d "$spec" ]]; then
        echo "$spec"
        return
    fi
    local dir="$WORK/$role-${spec//\//_}"
    if [[ ! -d "$dir" ]]; then
        echo "==> downloading $spec" >&2
        for attempt in $(seq 1 20); do
            hf download "$spec" --local-dir "$dir" --quiet >&2 && break
            echo "    retry $attempt" >&2
            sleep 5
        done
    fi
    echo "$dir"
}

BASE_DIR="$(resolve "$BASE" base)"
ADAPTER_DIR="$(resolve "$ADAPTER" adapter)"

# Reusing merged weights is only safe when they came from the same two inputs, and
# «the directory exists» does not say that. Skipping the merge because a previous
# run left one behind is how a build of one model came out as another — twice, and
# the quantizations were the right size and carried the new name both times. The
# stamp records what produced these weights; anything else re-merges.
MERGED="$OUT/merged-$NAME"
STAMP="$MERGED/.inputs"
# Content, not paths. A repository that is overwritten keeps its name, so a stamp
# made of directory names says «same inputs» about different weights — which is the
# same mistake one level up from the one this stamp was added to prevent.
WANT="$BASE_DIR|$ADAPTER_DIR|$(find "$ADAPTER_DIR" -name '*.safetensors' -exec shasum -a 256 {} + | shasum -a 256 | cut -d' ' -f1)"
if [[ ! -f "$MERGED/model.safetensors" || "$(cat "$STAMP" 2>/dev/null)" != "$WANT" ]]; then
    echo "==> merging"
    rm -rf "$MERGED"
    python "$ROOT/scripts/merge_lora.py" "$BASE_DIR" "$ADAPTER_DIR" "$MERGED"
    printf '%s' "$WANT" > "$STAMP"
fi

F16="$WORK/$NAME-f16.gguf"
if [[ ! -f "$F16" ]]; then
    echo "==> converting to GGUF"
    python "$LLAMA_DIR/convert_hf_to_gguf.py" "$MERGED" --outfile "$F16" --outtype f16
fi

QUANTIZE="$WORK/llama-build/bin/llama-quantize"
if [[ ! -x "$QUANTIZE" ]]; then
    echo "==> building llama-quantize"
    cmake -S "$LLAMA_DIR" -B "$WORK/llama-build" -DCMAKE_BUILD_TYPE=Release \
        -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TESTS=OFF \
        -DLLAMA_BUILD_APP=OFF -DLLAMA_BUILD_UI=OFF -DLLAMA_CURL=OFF > "$WORK/cmake.log" 2>&1
    cmake --build "$WORK/llama-build" --target llama-quantize -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc)" \
        >> "$WORK/cmake.log" 2>&1
fi

for TYPE in "${QUANTS[@]}"; do
    TARGET="$OUT/$NAME-$TYPE.gguf"
    [[ -f "$TARGET" ]] && { echo "==> $TYPE already present"; continue; }
    echo "==> quantizing $TYPE"
    "$QUANTIZE" "$F16" "$TARGET" "$TYPE" > "$WORK/quantize-$TYPE.log" 2>&1
done

echo "==> done"
ls -la "$OUT"/*.gguf | awk '{printf "  %-48s %6.2f GB\n", $NF, $5/1073741824}'
echo
echo "Merged weights: $MERGED"
echo "Intermediates are in $WORK and can be deleted."
