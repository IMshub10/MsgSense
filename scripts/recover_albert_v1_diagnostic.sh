#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="com.utilities.msgsense.benchmark.albertv50"
PROTOCOL="ner-v50-bg-v1"
REMOTE="/sdcard/Android/data/$PACKAGE/files/ner-benchmark-exports/$PROTOCOL"
DESTINATION="$ROOT/benchmark-results/diagnostics/$PROTOCOL/albert"
POLL_SECONDS="${POLL_SECONDS:-60}"

mkdir -p "$DESTINATION"
while true; do
  if adb shell find "$REMOTE" -name '*.sha256' 2>/dev/null | grep -q '\.sha256$'; then
    adb pull "$REMOTE/." "$DESTINATION/"
    python3 "$ROOT/scripts/verify_ner_benchmark_export.py" "$DESTINATION" \
      --protocol "$PROTOCOL" --min-runs 1 --output "$DESTINATION/comparison.csv"
    printf 'Diagnostic only. Never include this v1 run in the v2 comparison.\n' \
      >"$DESTINATION/DIAGNOSTIC_ONLY.txt"
    echo "Recovered and verified ALBERT v1 diagnostic export at $DESTINATION"
    exit 0
  fi
  if ! adb shell dumpsys activity services "$PACKAGE" 2>/dev/null |
    grep -q 'androidx.work.impl.foreground.SystemForegroundService'; then
    echo "ALBERT v1 worker disappeared before producing an export" >&2
    exit 1
  fi
  sleep "$POLL_SECONDS"
done
