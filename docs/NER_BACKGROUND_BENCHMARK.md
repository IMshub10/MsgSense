# ONNX NER v50 benchmark

The benchmark separates the two decisions that matter:

- `background-performance`: compare runtime cost while the app UI is backgrounded.
- `foreground-accuracy`: compare full-fixture diagnostic F1 while the app UI stays foregrounded.

The foreground score uses the promoted models' 6,445-row training fixture, so
it is a diagnostic consistency score, not an unbiased production-quality
estimate. Final quality selection still requires a separate held-out fixture.

## Assets and fixed performance subset

Prepare all model assets from a MsgSenseML checkout. The path is explicit so
the scripts never depend on a developer-specific directory:

```bash
MSGSENSE_ROOT=/path/to/MsgSenseML scripts/sync_ner_benchmark_assets.sh
```

The generated assets, model files, fixtures, goldens, APKs, and result exports
are intentionally ignored by Git. The sync script rejects missing source
artifacts and writes a SHA-256 manifest. Gradle and the matrix runner verify the
manifest before using a flavor.

The asset script creates `performance-selection.json`, a deterministic
entity-signature-stratified selection of 500 fixture IDs. The same selection is
packaged in all four flavors, covers every entity-signature combination in the
full fixture, and is protected by its SHA-256 hash.

## Background performance

Run one exploratory pass across all four models:

```bash
scripts/run_ner_benchmark_matrix.sh
```

The runner requires exactly one authorized ADB device and aborts on missing
tools, invalid assets, failed notification startup, worker interruption,
failed export verification, or a protocol mismatch.

Use `ROUNDS=3` later when variability and a more defensible performance ranking
are needed.

Protocol `ner-v50-bg500-v1` processes 25 excluded warm-ups followed by the fixed
500 measured records. It checkpoints every 100 cases and reports model load,
latency percentiles, compute throughput, wall throughput, persistence, memory,
battery, thermal status, failures, and parity.

Accepted exports live under:

```text
benchmark-results/ner-v50-bg500-v1/
```

## Full-fixture diagnostic accuracy

Accuracy does not depend on Android foreground/background scheduling. Generate
the full 6,445-row diagnostic accuracy table directly from the verified,
model-specific Python ONNX goldens:

```bash
python3 scripts/generate_ner_accuracy_table.py
```

This produces global and per-entity-type precision, recall, F1, and token
accuracy under:

```text
benchmark-results/ner-v50-host-accuracy-v1/
```

`scripts/run_ner_accuracy_matrix.sh` remains available when a full Android
foreground parity run is specifically needed, but it is not required to compare
model quality.

## Acceptance and recovery

Room remains temporary run storage. Every 100-case checkpoint atomically
replaces a non-authoritative live JSON/CSV/checksum export. Completed exports
are pulled and verified on the host before app data is cleared.

The matrix runner is resumable and skips only verified completed slots. If a
worker disappears, its latest live export is pulled, marked `INTERRUPTED`, and
stored under `benchmark-results/diagnostics/`; it is never included in a
comparison table.

## Local validation

Run the portable host checks:

```bash
python3 -m unittest discover -s scripts/tests -p 'test_*.py' -v
bash -n scripts/*.sh
```

Older banking NER JVM tests depend on ignored golden data and a locally built
host tokenizer JNI library. They report a JUnit skip when those prerequisites
are absent; the benchmark unit and instrumentation tests remain mandatory.
