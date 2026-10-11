# NER v50 preliminary findings

## Decision

MobileBERT v50 is the recommended candidate for production integration because
it had the lowest background latency and highest throughput while retaining a
high diagnostic entity F1 score.

## Background performance

One verified `ner-v50-bg500-v1` run per model was completed on a Samsung
SM-S911B. Each run used the same deterministic 500-case subset, 25 excluded
warm-ups, sequential inference, 100-case checkpoints, and host-verified
exports.

| Model | p50 latency | p95 latency | Wall throughput | Peak PSS |
| --- | ---: | ---: | ---: | ---: |
| ALBERT v50 | 2333 ms | 2860 ms | 0.51 msg/s | 243 MiB |
| BERT-base v50 | 1233 ms | 1481 ms | 0.83 msg/s | 611 MiB |
| DistilBERT v50 | 627 ms | 743 ms | 1.70 msg/s | 437 MiB |
| MobileBERT v50 | 425 ms | 579 ms | 3.14 msg/s | 294 MiB |

## Diagnostic accuracy

Full-fixture host evaluation used the 6,445-row training fixture and
model-specific Python ONNX goldens.

| Model | Entity F1 |
| --- | ---: |
| ALBERT v50 | 0.9970 |
| BERT-base v50 | 0.9959 |
| DistilBERT v50 | 0.9956 |
| MobileBERT v50 | 0.9924 |

## Limitations

- Background values are preliminary because only one verified run per model
  was completed; variability has not been measured.
- Accuracy values are diagnostic training-fixture scores, not held-out
  production-quality estimates.
- Raw exports, fixtures, model assets, APKs, checksums, and device identifiers
  are intentionally not committed.
