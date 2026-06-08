#!/usr/bin/env python3
"""Generate full-fixture diagnostic accuracy from model-specific Python ONNX goldens."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import defaultdict
from pathlib import Path

FLAVORS = {
    "albertV50": "albert-v50",
    "bertBaseV50": "bert-base-v50",
    "distilbertV50": "distilbert-v50",
    "mobilebertV50": "mobilebert-v50",
}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def entities(tokens: list[str], labels: list[str]) -> set[tuple[str, str, int, int]]:
    result = set()
    start = -1
    kind = ""
    for index, label in enumerate(labels + ["O"]):
        if label.startswith("B-") or label == "O" or (
            label.startswith("I-") and kind and label[2:] != kind
        ):
            if start >= 0:
                result.add((kind, " ".join(tokens[start:index]), start, index - 1))
                start, kind = -1, ""
        if label.startswith("B-"):
            start, kind = index, label[2:]
        elif label.startswith("I-") and start < 0:
            start, kind = index, label[2:]
    return result


def scores(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    precision = tp / max(tp + fp, 1)
    recall = tp / max(tp + fn, 1)
    f1 = 0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)
    return precision, recall, f1


def evaluate(asset_dir: Path, model_id: str) -> tuple[dict, list[dict]]:
    fixtures = {
        row["id"]: row
        for row in map(json.loads, asset_dir.joinpath("fixture.jsonl").read_text().splitlines())
    }
    goldens = {
        row["id"]: row
        for row in map(json.loads, asset_dir.joinpath("golden.jsonl").read_text().splitlines())
    }
    if fixtures.keys() != goldens.keys() or len(fixtures) != 6445:
        raise ValueError(f"{asset_dir} does not contain one matching 6,445-case fixture/golden set")

    tp = fp = fn = correct_tokens = total_tokens = 0
    by_type = defaultdict(lambda: [0, 0, 0])
    for fixture_id, fixture in fixtures.items():
        predicted_labels = goldens[fixture_id]["word_labels"]
        expected_labels = fixture["labels"][: len(predicted_labels)]
        correct_tokens += sum(a == b for a, b in zip(expected_labels, predicted_labels))
        total_tokens += len(expected_labels)
        expected = entities(fixture["tokens"][: len(expected_labels)], expected_labels)
        predicted = entities(fixture["tokens"][: len(predicted_labels)], predicted_labels)
        matched = expected & predicted
        tp += len(matched)
        fp += len(predicted - expected)
        fn += len(expected - predicted)
        for entity in matched:
            by_type[entity[0]][0] += 1
        for entity in predicted - expected:
            by_type[entity[0]][1] += 1
        for entity in expected - predicted:
            by_type[entity[0]][2] += 1

    precision, recall, f1 = scores(tp, fp, fn)
    summary = {
        "model_id": model_id,
        "cases": len(fixtures),
        "token_accuracy": correct_tokens / total_tokens,
        "entity_precision": precision,
        "entity_recall": recall,
        "entity_f1": f1,
        "true_positive": tp,
        "false_positive": fp,
        "false_negative": fn,
        "fixture_sha256": sha256(asset_dir / "fixture.jsonl"),
        "golden_sha256": sha256(asset_dir / "golden.jsonl"),
        "model_sha256": sha256(asset_dir / "model.onnx"),
    }
    per_type = []
    for kind, (kind_tp, kind_fp, kind_fn) in sorted(by_type.items()):
        kind_precision, kind_recall, kind_f1 = scores(kind_tp, kind_fp, kind_fn)
        per_type.append(
            {
                "model_id": summary["model_id"],
                "entity_type": kind,
                "precision": kind_precision,
                "recall": kind_recall,
                "f1": kind_f1,
                "true_positive": kind_tp,
                "false_positive": kind_fp,
                "false_negative": kind_fn,
            }
        )
    return summary, per_type


def write_csv(path: Path, rows: list[dict]) -> None:
    with path.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--app-src", type=Path, default=Path("app/src"))
    parser.add_argument("--output-dir", type=Path, default=Path("benchmark-results/ner-v50-host-accuracy-v1"))
    args = parser.parse_args()
    summaries, per_type = [], []
    for flavor, model_id in FLAVORS.items():
        summary, type_rows = evaluate(args.app_src / flavor / "assets/ner_benchmark", model_id)
        summaries.append(summary)
        per_type.extend(type_rows)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(args.output_dir / "accuracy.csv", summaries)
    write_csv(args.output_dir / "accuracy-by-entity-type.csv", per_type)


if __name__ == "__main__":
    main()
