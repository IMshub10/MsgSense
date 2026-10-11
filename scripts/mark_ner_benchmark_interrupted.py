#!/usr/bin/env python3
"""Mark pulled live benchmark checkpoints as interrupted diagnostics."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import time
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_write(path: Path, contents: str) -> None:
    temporary = path.with_name(f"{path.name}.tmp")
    temporary.write_text(contents)
    os.replace(temporary, path)


def mark(summary_path: Path, reason: str) -> None:
    summary = json.loads(summary_path.read_text())
    summary["status"] = "INTERRUPTED"
    summary["ended_at_ms"] = int(time.time() * 1000)
    summary["interruption_reason"] = reason
    atomic_write(summary_path, json.dumps(summary, indent=2) + "\n")
    cases_path = summary_path.with_name(summary["cases_file"])
    checksum_path = summary_path.with_suffix(".sha256")
    atomic_write(
        checksum_path,
        f"{sha256(summary_path)}  {summary_path.name}\n"
        f"{sha256(cases_path)}  {cases_path.name}\n",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", type=Path)
    parser.add_argument("--reason", required=True)
    args = parser.parse_args()
    summaries = list(args.root.rglob("*.live.json"))
    if not summaries:
        raise SystemExit(f"No live summaries found below {args.root}")
    for summary in summaries:
        mark(summary, args.reason)


if __name__ == "__main__":
    main()
