#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
: "${MSGSENSE_ROOT:?Set MSGSENSE_ROOT to the MsgSenseML checkout before syncing benchmark assets.}"
EXPORT_ROOT="$MSGSENSE_ROOT/ner/exports/shareable/v50"
MODEL_ROOT="$MSGSENSE_ROOT/ner/models/prod"
PYTHON="${PYTHON:-$MSGSENSE_ROOT/.venv/bin/python}"
[[ -d "$MSGSENSE_ROOT" ]] || { echo "MSGSENSE_ROOT is not a directory: $MSGSENSE_ROOT" >&2; exit 1; }
[[ -x "$PYTHON" ]] || { echo "Missing Python interpreter: $PYTHON" >&2; exit 1; }

MODEL_FLAVOR_PAIRS=(
  "albert-v50:albertV50"
  "bert-base-v50:bertBaseV50"
  "distilbert-v50:distilbertV50"
  "mobilebert-v50:mobilebertV50"
)

selection_source=""
for pair in "${MODEL_FLAVOR_PAIRS[@]}"; do
  model_id="${pair%%:*}"
  flavor="${pair##*:}"
  source="$EXPORT_ROOT/$model_id"
  target="$ROOT/app/src/$flavor/assets/ner_benchmark"
  mkdir -p "$target"

  for required in "$source/$model_id.onnx" "$source/model/tokenizer.json" \
    "$source/model/config.json" "$source/export_info.json" \
    "$MODEL_ROOT/$model_id/dataset.jsonl"; do
    [[ -f "$required" ]] || { echo "Missing required artifact: $required" >&2; exit 1; }
  done

  cp "$source/$model_id.onnx" "$target/model.onnx"
  cp "$source/model/tokenizer.json" "$target/tokenizer.json"
  cp "$source/model/config.json" "$target/config.json"
  cp "$source/export_info.json" "$target/export_info.json"
  cp "$MODEL_ROOT/$model_id/dataset.jsonl" "$target/fixture.jsonl"
  if [[ -z "$selection_source" ]]; then
    "$PYTHON" "$ROOT/scripts/select_ner_performance_fixture.py" \
      --fixture "$target/fixture.jsonl" \
      --output "$target/performance-selection.json"
    selection_source="$target/performance-selection.json"
  else
    cp "$selection_source" "$target/performance-selection.json"
  fi

  "$PYTHON" "$ROOT/scripts/generate_ner_benchmark_golden.py" \
    --model-dir "$source/model" \
    --onnx "$source/$model_id.onnx" \
    --fixture "$MODEL_ROOT/$model_id/dataset.jsonl" \
    --output "$target/golden.jsonl"

  (
    cd "$target"
    shasum -a 256 model.onnx tokenizer.json config.json export_info.json fixture.jsonl golden.jsonl \
      performance-selection.json \
      > manifest.sha256
  )
  echo "Prepared $model_id assets in $target"
done
