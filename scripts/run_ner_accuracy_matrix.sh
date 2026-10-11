#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE=foreground-accuracy ROUNDS=1 RUN_PREFLIGHT=0 \
  "$ROOT/scripts/run_ner_benchmark_matrix.sh"
