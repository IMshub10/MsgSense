#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CRATE_DIR="$ROOT/core/src/main/rust/hf_tokenizer_jni"
OUT_DIR="$ROOT/core/src/main/jniLibs"

mkdir -p "$OUT_DIR"

pushd "$CRATE_DIR" >/dev/null
cargo ndk -t arm64-v8a -t x86_64 -o "$OUT_DIR" build --release
popd >/dev/null

echo "Built JNI libs into $OUT_DIR"
