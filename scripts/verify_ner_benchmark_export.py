#!/usr/bin/env python3
"""Verify completed benchmark exports and generate host-side comparison tables."""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import statistics
from pathlib import Path
from typing import Optional

DEFAULT_PROTOCOL = "ner-v50-bg500-v1"


class VerificationError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise VerificationError(message)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def percentile(values: list[float], fraction: float) -> float:
    ordered = sorted(values)
    return ordered[int((len(ordered) - 1) * fraction)]


def close(actual: object, expected: float, field: str, tolerance: float = 0.02) -> None:
    require(abs(float(actual) - expected) <= tolerance, f"{field} does not recalculate")


def verify(
    summary_path: Path,
    protocol: str,
    expected_hashes: Optional[dict[str, str]] = None,
    expected_apk_hash: Optional[str] = None,
) -> dict:
    require(not summary_path.name.endswith(".live.json"), f"{summary_path} is a non-authoritative live export")
    summary = json.loads(summary_path.read_text())
    cases_path = summary_path.with_name(summary["cases_file"])
    checksum_path = summary_path.with_suffix(".sha256")
    require(summary["protocol_version"] == protocol, f"{summary_path} uses the wrong protocol")
    require(summary["status"] == "COMPLETED", f"{summary_path} is not completed")
    expected_cases = int(summary["expected_cases"])
    require(summary["measured_cases"] == expected_cases, f"{summary_path} has the wrong case count")
    expected_background = summary["benchmark_mode"] == "background-performance"
    require(summary["background_verified"] is expected_background, f"{summary_path} has the wrong UI-state verification")
    if expected_hashes:
        for export_field, asset_name in (
            ("model_sha256", "model.onnx"),
            ("tokenizer_sha256", "tokenizer.json"),
            ("fixture_sha256", "fixture.jsonl"),
            ("golden_sha256", "golden.jsonl"),
        ):
            require(summary[export_field] == expected_hashes[asset_name], f"{export_field} does not match manifest")
        if expected_background:
            require(
                summary["selection_sha256"] == expected_hashes["performance-selection.json"],
                "selection_sha256 does not match manifest",
            )
    if expected_apk_hash:
        require(summary["apk_sha256"] == expected_apk_hash, f"{summary_path} does not match the installed APK")
    require(cases_path.is_file(), f"Missing cases file for {summary_path}")
    require(checksum_path.is_file(), f"Missing checksum file for {summary_path}")

    checksums = {
        line.split(maxsplit=1)[1].strip(): line.split(maxsplit=1)[0]
        for line in checksum_path.read_text().splitlines()
        if line.strip()
    }
    require(checksums.get(summary_path.name) == sha256(summary_path), f"Bad summary checksum: {summary_path}")
    require(checksums.get(cases_path.name) == sha256(cases_path), f"Bad cases checksum: {cases_path}")

    with cases_path.open(newline="") as stream:
        rows = list(csv.DictReader(stream))
    ids = [row["fixture_id"] for row in rows]
    require(len(ids) == expected_cases, f"{cases_path} does not contain {expected_cases} rows")
    require(len(set(ids)) == expected_cases, f"{cases_path} contains duplicate fixture IDs")
    require(all(row["tokenizer_parity"] == "true" for row in rows), f"{cases_path} has tokenizer drift")
    require(all(row["argmax_parity"] == "true" for row in rows), f"{cases_path} has ONNX argmax drift")

    totals = [float(row["total_ms"]) for row in rows]
    for field, fraction in (
        ("total_p50_ms", 0.50),
        ("total_p90_ms", 0.90),
        ("total_p95_ms", 0.95),
        ("total_p99_ms", 0.99),
    ):
        close(summary[field], percentile(totals, fraction), field)
    for prefix in ("tokenizer", "inference", "decode"):
        values = [float(row[f"{prefix}_ms"]) for row in rows]
        close(summary[f"{prefix}_p50_ms"], percentile(values, 0.50), f"{prefix}_p50_ms")
        close(summary[f"{prefix}_p95_ms"], percentile(values, 0.95), f"{prefix}_p95_ms")

    for field in ("true_positive", "false_positive", "false_negative"):
        require(sum(int(row[field]) for row in rows) == int(summary[field]), f"{field} does not recalculate")
    tp = int(summary["true_positive"])
    fp = int(summary["false_positive"])
    fn = int(summary["false_negative"])
    precision = tp / max(tp + fp, 1)
    recall = tp / max(tp + fn, 1)
    f1 = 0.0 if precision + recall == 0 else 2 * precision * recall / (precision + recall)
    close(summary["precision"], precision, "precision", tolerance=0.000001)
    close(summary["recall"], recall, "recall", tolerance=0.000001)
    close(summary["f1"], f1, "f1", tolerance=0.000001)
    expected_counts = {
        "failures": sum(bool(row["error"]) for row in rows),
        "tokenizer_parity_failures": sum(row["tokenizer_parity"] != "true" for row in rows),
        "entity_parity_failures": sum(row["entity_parity"] != "true" for row in rows),
        "argmax_parity_failures": sum(row["argmax_parity"] != "true" for row in rows),
    }
    for field, count in expected_counts.items():
        require(int(summary[field]) == count, f"{field} does not recalculate")

    if protocol in ("ner-v50-bg500-v1", "ner-v50-fg-accuracy-v1"):
        compute_elapsed = sum(totals)
        close(summary["compute_elapsed_ms"], compute_elapsed, "compute_elapsed_ms", tolerance=0.1)
        compute_throughput = expected_cases * 1000.0 / max(compute_elapsed, 0.001)
        wall_elapsed = float(summary["wall_elapsed_ms"])
        close(summary["compute_throughput_msgs_sec"], compute_throughput, "compute_throughput_msgs_sec")
        close(
            summary["wall_throughput_msgs_sec"],
            expected_cases * 1000.0 / max(wall_elapsed, 0.001),
            "wall_throughput_msgs_sec",
        )
        close(summary["throughput_msgs_sec"], float(summary["wall_throughput_msgs_sec"]), "throughput_msgs_sec")
        require(wall_elapsed >= compute_elapsed, "wall elapsed time is shorter than measured compute time")
        require(summary["last_checkpoint_at_ms"] is not None, "completed v2 run has no checkpoint metadata")
    else:
        summary.setdefault("compute_throughput_msgs_sec", summary["throughput_msgs_sec"])
        summary.setdefault("wall_throughput_msgs_sec", summary["throughput_msgs_sec"])
    return summary


def median(runs: list[dict], field: str) -> float:
    return statistics.median(float(run[field]) for run in runs)


def variability(runs: list[dict], field: str) -> float:
    values = [float(run[field]) for run in runs]
    return statistics.stdev(values) if len(values) > 1 else 0.0


def write_tables(summaries: list[dict], output: Path, min_runs: int, exact_runs: bool) -> None:
    grouped: dict[tuple[str, str, str], list[dict]] = {}
    for summary in summaries:
        grouped.setdefault((summary["device_id"], summary["benchmark_mode"], summary["model_id"]), []).append(summary)
    require(len({summary["fixture_sha256"] for summary in summaries}) == 1, "Accepted runs use different fixtures")
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(
            [
                "device_id", "benchmark_mode", "model_id", "accepted_runs", "median_p50_ms", "p50_stdev_ms",
                "median_p95_ms", "p95_stdev_ms", "median_compute_throughput_msgs_sec",
                "compute_throughput_stdev", "median_wall_throughput_msgs_sec",
                "wall_throughput_stdev", "median_peak_pss_kb", "median_peak_heap_kb",
                "median_charge_delta_uah", "median_max_thermal_status", "median_f1",
                "median_failures", "median_entity_parity_failures",
            ]
        )
        for (device_id, benchmark_mode, model_id), runs in sorted(grouped.items()):
            if exact_runs:
                require(len(runs) == min_runs, f"{model_id} does not have exactly {min_runs} verified runs")
            else:
                require(len(runs) >= min_runs, f"{model_id} has fewer than {min_runs} verified runs")
            for field in ("model_sha256", "tokenizer_sha256", "golden_sha256"):
                require(len({run[field] for run in runs}) == 1, f"{model_id} uses multiple {field} values")
            if benchmark_mode == "background-performance":
                require(len({run["selection_sha256"] for run in runs}) == 1, f"{model_id} uses multiple selections")
            writer.writerow(
                [
                    device_id, benchmark_mode, model_id, len(runs),
                    median(runs, "total_p50_ms"), variability(runs, "total_p50_ms"),
                    median(runs, "total_p95_ms"), variability(runs, "total_p95_ms"),
                    median(runs, "compute_throughput_msgs_sec"), variability(runs, "compute_throughput_msgs_sec"),
                    median(runs, "wall_throughput_msgs_sec"), variability(runs, "wall_throughput_msgs_sec"),
                    median(runs, "peak_pss_kb"), median(runs, "peak_heap_kb"),
                    statistics.median(
                        float(run["start_charge_uah"]) - float(run["end_charge_uah"]) for run in runs
                    ),
                    median(runs, "max_thermal_status"), median(runs, "f1"),
                    median(runs, "failures"), median(runs, "entity_parity_failures"),
                ]
            )

    runs_output = output.with_name(f"{output.stem}-runs{output.suffix}")
    with runs_output.open("w", newline="") as stream:
        fields = [
            "device_id", "benchmark_mode", "model_id", "run_id", "total_p50_ms", "total_p95_ms",
            "compute_throughput_msgs_sec", "wall_throughput_msgs_sec", "peak_pss_kb",
            "peak_heap_kb", "max_thermal_status", "f1", "failures",
            "tokenizer_parity_failures", "argmax_parity_failures", "entity_parity_failures",
        ]
        writer = csv.DictWriter(stream, fields)
        writer.writeheader()
        for summary in sorted(
            summaries,
            key=lambda run: (run["device_id"], run["benchmark_mode"], run["model_id"], run["started_at_ms"]),
        ):
            writer.writerow({field: summary[field] for field in fields})


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--output", type=Path, default=Path("comparison.csv"))
    parser.add_argument("--min-runs", type=int, default=3)
    parser.add_argument("--exact-runs", action="store_true")
    parser.add_argument("--protocol", default=DEFAULT_PROTOCOL)
    parser.add_argument("--manifest", type=Path)
    parser.add_argument("--apk", type=Path)
    args = parser.parse_args()
    paths = [path for path in args.root.rglob("*.json") if not path.name.endswith(".live.json")]
    require(bool(paths), f"No completed export summaries found below {args.root}")
    expected_hashes = None
    if args.manifest:
        expected_hashes = {
            line.split(maxsplit=1)[1].strip().removeprefix("*"): line.split(maxsplit=1)[0]
            for line in args.manifest.read_text().splitlines()
            if line.strip()
        }
    expected_apk_hash = sha256(args.apk) if args.apk else None
    summaries = [verify(path, args.protocol, expected_hashes, expected_apk_hash) for path in paths]
    write_tables(summaries, args.output, args.min_runs, args.exact_runs)


if __name__ == "__main__":
    main()
