import csv
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import verify_ner_benchmark_export as verifier  # noqa: E402
import mark_ner_benchmark_interrupted as interrupted  # noqa: E402
import select_ner_performance_fixture as selector  # noqa: E402


class VerifyNerBenchmarkExportTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)

    def tearDown(self):
        self.temporary.cleanup()

    def test_completed_v2_export_is_verified(self):
        summary_path = self.write_export()
        self.assertEqual("COMPLETED", verifier.verify(summary_path, verifier.DEFAULT_PROTOCOL)["status"])

    def test_live_export_is_never_accepted(self):
        live = self.root / "run.live.json"
        live.write_text("{}")
        with self.assertRaises(verifier.VerificationError):
            verifier.verify(live, verifier.DEFAULT_PROTOCOL)

    def test_decoded_entity_drift_is_reported_but_does_not_reject_model_runtime(self):
        summary_path = self.write_export(entity_parity=False)
        self.assertEqual(2, verifier.verify(summary_path, verifier.DEFAULT_PROTOCOL)["entity_parity_failures"])

    def test_argmax_drift_is_rejected(self):
        summary_path = self.write_export(argmax_parity=False)
        with self.assertRaises(verifier.VerificationError):
            verifier.verify(summary_path, verifier.DEFAULT_PROTOCOL)

    def test_recovered_live_export_is_marked_interrupted_and_still_rejected(self):
        summary_path = self.write_export().rename(self.root / "run.live.json")
        cases_path = self.root / "run-cases.csv"
        live_cases = cases_path.rename(self.root / "run.live-cases.csv")
        summary = json.loads(summary_path.read_text())
        summary["cases_file"] = live_cases.name
        summary_path.write_text(json.dumps(summary))
        interrupted.mark(summary_path, "process-loss")

        self.assertEqual("INTERRUPTED", json.loads(summary_path.read_text())["status"])
        with self.assertRaises(verifier.VerificationError):
            verifier.verify(summary_path, verifier.DEFAULT_PROTOCOL)

    def write_export(self, entity_parity=True, argmax_parity=True):
        summary_path = self.root / "run.json"
        cases_path = self.root / "run-cases.csv"
        checksum_path = self.root / "run.sha256"
        rows = [
            {
                "fixture_id": index,
                "tokenizer_ms": 1.0,
                "inference_ms": total - 2.0,
                "decode_ms": 1.0,
                "total_ms": total,
                "argmax_parity": "true" if argmax_parity else "false",
                "tokenizer_parity": "true",
                "entity_parity": "true" if entity_parity else "false",
                "true_positive": 1,
                "false_positive": 0,
                "false_negative": 0,
                "error": "",
            }
            for index, total in ((10, 10.0), (20, 30.0))
        ]
        with cases_path.open("w", newline="") as stream:
            writer = csv.DictWriter(stream, rows[0].keys())
            writer.writeheader()
            writer.writerows(rows)
        summary = {
            "protocol_version": verifier.DEFAULT_PROTOCOL,
            "benchmark_mode": "background-performance",
            "expected_cases": 2,
            "selection_sha256": "s",
            "run_id": "run",
            "model_id": "albert-v50",
            "status": "COMPLETED",
            "started_at_ms": 1,
            "measured_cases": 2,
            "cases_file": cases_path.name,
            "background_verified": True,
            "total_p50_ms": 10.0,
            "total_p90_ms": 10.0,
            "total_p95_ms": 10.0,
            "total_p99_ms": 10.0,
            "tokenizer_p50_ms": 1.0,
            "tokenizer_p95_ms": 1.0,
            "inference_p50_ms": 8.0,
            "inference_p95_ms": 8.0,
            "decode_p50_ms": 1.0,
            "decode_p95_ms": 1.0,
            "true_positive": 2,
            "false_positive": 0,
            "false_negative": 0,
            "precision": 1.0,
            "recall": 1.0,
            "f1": 1.0,
            "failures": 0,
            "tokenizer_parity_failures": 0,
            "entity_parity_failures": 0 if entity_parity else 2,
            "argmax_parity_failures": 0 if argmax_parity else 2,
            "compute_elapsed_ms": 40.0,
            "wall_elapsed_ms": 100.0,
            "compute_throughput_msgs_sec": 50.0,
            "wall_throughput_msgs_sec": 20.0,
            "throughput_msgs_sec": 20.0,
            "last_checkpoint_at_ms": 99,
        }
        summary_path.write_text(json.dumps(summary))
        checksum_path.write_text(
            f"{self.digest(summary_path)}  {summary_path.name}\n"
            f"{self.digest(cases_path)}  {cases_path.name}\n"
        )
        return summary_path

    @staticmethod
    def digest(path):
        return hashlib.sha256(path.read_bytes()).hexdigest()


class SelectNerPerformanceFixtureTest(unittest.TestCase):
    def test_selection_is_deterministic_and_covers_every_signature(self):
        rows = [
            {"id": 1, "labels": ["B-BANK"]},
            {"id": 2, "labels": ["B-BANK"]},
            {"id": 3, "labels": ["B-AMOUNT"]},
            {"id": 4, "labels": ["B-AMOUNT"]},
            {"id": 5, "labels": ["B-DATE"]},
            {"id": 6, "labels": ["B-DATE"]},
        ]
        first = selector.select(rows, 3, "seed")
        second = selector.select(rows, 3, "seed")
        signatures = {selector.signature(row) for row in rows if row["id"] in first}

        self.assertEqual(first, second)
        self.assertEqual({("BANK",), ("AMOUNT",), ("DATE",)}, signatures)


if __name__ == "__main__":
    unittest.main()
