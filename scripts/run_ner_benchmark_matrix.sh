#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing required command: $1" >&2; exit 1; }
}

for command in adb awk python3 shasum find grep; do
  require_command "$command"
done

CONNECTED_DEVICES="$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')"
DEVICE_COUNT="$(printf '%s\n' "$CONNECTED_DEVICES" | awk 'NF { count++ } END { print count + 0 }')"
if (( DEVICE_COUNT != 1 )); then
  echo "Expected exactly one authorized Android device; found $DEVICE_COUNT." >&2
  adb devices -l >&2
  exit 1
fi

MODE="${MODE:-background-performance}"
case "$MODE" in
  background-performance)
    PROTOCOL="ner-v50-bg500-v1"
    DEFAULT_ROUNDS=1
    ;;
  foreground-accuracy)
    PROTOCOL="ner-v50-fg-accuracy-v1"
    DEFAULT_ROUNDS=1
    ;;
  *)
    echo "Unknown MODE: $MODE" >&2
    exit 1
    ;;
esac
ROUNDS="${ROUNDS:-$DEFAULT_ROUNDS}"
MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-14400}"
STARTUP_WAIT_SECONDS="${STARTUP_WAIT_SECONDS:-90}"
RUN_PREFLIGHT="${RUN_PREFLIGHT:-1}"
RESULT_ROOT="$ROOT/benchmark-results/$PROTOCOL"
DIAGNOSTIC_ROOT="$ROOT/benchmark-results/diagnostics/$PROTOCOL"
FLAVORS=(albertV50 bertBaseV50 distilbertV50 mobilebertV50)

for flavor in "${FLAVORS[@]}"; do
  asset_dir="$ROOT/app/src/$flavor/assets/ner_benchmark"
  [[ -f "$asset_dir/manifest.sha256" ]] || {
    echo "Missing generated assets for $flavor. Set MSGSENSE_ROOT and run scripts/sync_ner_benchmark_assets.sh." >&2
    exit 1
  }
  (cd "$asset_dir" && shasum -a 256 -c manifest.sha256 >/dev/null) || {
    echo "Asset verification failed for $flavor. Run scripts/sync_ner_benchmark_assets.sh." >&2
    exit 1
  }
done

mkdir -p "$RESULT_ROOT" "$DIAGNOSTIC_ROOT"
"$ROOT/gradlew" :app:buildRustTokenizerAndroid

device_export_root() {
  local package="$1"
  printf '/sdcard/Android/data/%s/files/ner-benchmark-exports/%s' "$package" "$PROTOCOL"
}

final_export_exists() {
  local package="$1"
  adb shell find "$(device_export_root "$package")" -name '*.sha256' 2>/dev/null |
    grep -v '\.live\.sha256$' |
    grep -q '\.sha256$'
}

live_export_exists() {
  local package="$1"
  adb shell find "$(device_export_root "$package")" -name '*.live.sha256' 2>/dev/null |
    grep -q '\.live\.sha256$'
}

worker_active() {
  local package="$1"
  adb shell dumpsys activity services "$package" 2>/dev/null |
    grep -q 'androidx.work.impl.foreground.SystemForegroundService'
}

slot_verified() {
  local destination="$1"
  local flavor="$2"
  find "$destination" -name '*.json' ! -name '*.live.json' -print -quit 2>/dev/null | grep -q . &&
    python3 "$ROOT/scripts/verify_ner_benchmark_export.py" "$destination" \
      --min-runs 1 --exact-runs --output "$destination/comparison.csv" \
      --manifest "$ROOT/app/src/$flavor/assets/ner_benchmark/manifest.sha256" >/dev/null
}

pull_live_diagnostic() {
  local package="$1"
  local flavor="$2"
  local round="$3"
  local reason="$4"
  if ! live_export_exists "$package"; then
    return 0
  fi
  local destination="$DIAGNOSTIC_ROOT/round-$round/$flavor/$(date +%Y%m%d-%H%M%S)-$reason"
  mkdir -p "$destination"
  adb pull "$(device_export_root "$package")/." "$destination/"
  python3 "$ROOT/scripts/mark_ner_benchmark_interrupted.py" "$destination" --reason "$reason"
  printf '%s\n' "$reason" >"$destination/recovery-reason.txt"
  echo "Preserved interrupted diagnostics at $destination"
}

verify_background_start() {
  local package="$1"
  local wait_started now notifications notification_keys services
  wait_started="$(date +%s)"
  while true; do
    if final_export_exists "$package"; then
      return 0
    fi
    notifications="$(adb shell dumpsys notification --noredact)"
    notification_keys="$(adb shell cmd notification list)"
    services="$(adb shell dumpsys activity services "$package")"
    if grep -Fq "$package" <<<"$notifications" &&
      grep -Fq "ner_benchmark" <<<"$notifications" &&
      grep -Fq "$package" <<<"$notification_keys" &&
      grep -Fq "androidx.work.impl.foreground.SystemForegroundService" <<<"$services"; then
      return 0
    fi
    now="$(date +%s)"
    if (( now - wait_started > STARTUP_WAIT_SECONDS )); then
      echo "Foreground worker/notification did not become active for $package" >&2
      adb shell cmd notification list | grep -F "$package" >&2 || true
      adb shell dumpsys activity services "$package" |
        grep -E "SystemForegroundService|packageName=" >&2 || true
      return 1
    fi
    sleep 2
  done
}

start_background_benchmark() {
  local package="$1"
  adb shell am start -W \
    --es benchmark_mode "$MODE" \
    -n "$package/com.summer.notifai.nerbenchmark.NerBenchmarkLaunchActivity"
  if [[ "$MODE" == "background-performance" ]]; then
    sleep 2
    adb shell input keyevent KEYCODE_HOME
  fi
}

wait_for_export() {
  local package="$1"
  local flavor="$2"
  local round="$3"
  local wait_started now inactive_checks=0
  wait_started="$(date +%s)"
  while ! final_export_exists "$package"; do
    if worker_active "$package"; then
      inactive_checks=0
    else
      inactive_checks=$((inactive_checks + 1))
      if (( inactive_checks >= 3 )); then
        pull_live_diagnostic "$package" "$flavor" "$round" "interrupted"
        echo "Worker disappeared before a completed export for $flavor round $round" >&2
        return 1
      fi
    fi
    now="$(date +%s)"
    if (( now - wait_started > MAX_WAIT_SECONDS )); then
      pull_live_diagnostic "$package" "$flavor" "$round" "timeout"
      echo "Timed out waiting for $flavor round $round" >&2
      return 1
    fi
    sleep 30
  done
}

if [[ "$MODE" == "background-performance" && "$RUN_PREFLIGHT" == "1" ]]; then
  preflight_package="com.utilities.msgsense.benchmark.albertv50"
  if worker_active "$preflight_package"; then
    echo "An ALBERT benchmark worker is already active; refusing to replace or clear it." >&2
    exit 1
  fi
  "$ROOT/gradlew" :app:assembleAlbertV50Debug
  preflight_apk="$(find "$ROOT/app/build/outputs/apk/albertV50/debug" -name '*.apk' | head -n 1)"
  adb install -r "$preflight_apk"
  adb shell pm clear "$preflight_package"
  adb shell pm grant "$preflight_package" android.permission.POST_NOTIFICATIONS
  start_background_benchmark "$preflight_package"
  verify_background_start "$preflight_package"
  echo "ALBERT foreground-notification preflight passed; clearing non-measured run."
  adb shell pm clear "$preflight_package"
fi

for ((round=1; round<=ROUNDS; round++)); do
  if (( round % 2 == 0 )); then
    order=(mobilebertV50 distilbertV50 bertBaseV50 albertV50)
  else
    order=("${FLAVORS[@]}")
  fi
  for flavor in "${order[@]}"; do
    destination="$RESULT_ROOT/round-$round/$flavor"
    if slot_verified "$destination" "$flavor"; then
      echo "Skipping verified $flavor round $round."
      continue
    fi
    case "$flavor" in
      albertV50)
        variant="AlbertV50Debug"
        package="com.utilities.msgsense.benchmark.albertv50"
        ;;
      bertBaseV50)
        variant="BertBaseV50Debug"
        package="com.utilities.msgsense.benchmark.bertbasev50"
        ;;
      distilbertV50)
        variant="DistilbertV50Debug"
        package="com.utilities.msgsense.benchmark.distilbertv50"
        ;;
      mobilebertV50)
        variant="MobilebertV50Debug"
        package="com.utilities.msgsense.benchmark.mobilebertv50"
        ;;
      *)
        echo "Unknown flavor: $flavor" >&2
        exit 1
        ;;
    esac
    "$ROOT/gradlew" ":app:assemble$variant"
    apk="$(find "$ROOT/app/build/outputs/apk/$flavor/debug" -name '*.apk' | head -n 1)"
    if worker_active "$package"; then
      echo "Reattaching to active $flavor round $round worker."
      verify_background_start "$package"
      wait_for_export "$package" "$flavor" "$round"
    elif final_export_exists "$package"; then
      echo "Collecting completed on-device $flavor round $round export."
    else
      adb install -r "$apk"
      adb shell pm clear "$package"
      adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
      if [[ "$MODE" == "background-performance" ]] && adb shell dumpsys activity activities |
        grep -E "mResumedActivity|topResumedActivity" |
        grep -Fq "$package"; then
        echo "$package has a visible Activity; benchmark requires the UI to be backgrounded" >&2
        exit 1
      fi
      start_background_benchmark "$package"
      verify_background_start "$package"
      echo "Running $MODE $flavor round $round. Waiting for a verified completed export..."
      wait_for_export "$package" "$flavor" "$round"
    fi
    mkdir -p "$destination"
    adb pull "$(device_export_root "$package")/." "$destination/"
    python3 "$ROOT/scripts/verify_ner_benchmark_export.py" "$destination" \
      --min-runs 1 --exact-runs --output "$destination/comparison.csv" \
      --manifest "$ROOT/app/src/$flavor/assets/ner_benchmark/manifest.sha256" \
      --apk "$apk"
    adb shell pm clear "$package"
  done
done

python3 "$ROOT/scripts/verify_ner_benchmark_export.py" "$RESULT_ROOT" \
  --min-runs "$ROUNDS" --exact-runs --output "$RESULT_ROOT/comparison.csv"
