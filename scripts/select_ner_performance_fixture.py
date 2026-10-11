#!/usr/bin/env python3
"""Select a deterministic, entity-signature-stratified NER performance subset."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
from collections import defaultdict
from pathlib import Path


def signature(row: dict) -> tuple[str, ...]:
    return tuple(sorted({label[2:] for label in row["labels"] if label != "O"}))


def stable_rank(seed: str, fixture_id: int) -> str:
    return hashlib.sha256(f"{seed}:{fixture_id}".encode()).hexdigest()


def select(rows: list[dict], size: int, seed: str) -> list[int]:
    groups: dict[tuple[str, ...], list[dict]] = defaultdict(list)
    for row in rows:
        groups[signature(row)].append(row)
    if len(groups) > size:
        raise ValueError(f"{len(groups)} entity signatures cannot fit in a {size}-case subset")

    quotas = {key: 1 for key in groups}
    remaining = size - len(groups)
    capacities = {key: len(values) - 1 for key, values in groups.items()}
    total_capacity = sum(capacities.values())
    fractional = {}
    for key, capacity in capacities.items():
        exact = remaining * capacity / total_capacity
        addition = min(capacity, math.floor(exact))
        quotas[key] += addition
        fractional[key] = exact - addition
    unassigned = size - sum(quotas.values())
    for key in sorted(groups, key=lambda item: (-fractional[item], item)):
        if not unassigned:
            break
        if quotas[key] < len(groups[key]):
            quotas[key] += 1
            unassigned -= 1

    selected = []
    for key, values in groups.items():
        ranked = sorted(values, key=lambda row: stable_rank(seed, row["id"]))
        selected.extend(row["id"] for row in ranked[: quotas[key]])
    return sorted(selected)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--size", type=int, default=500)
    parser.add_argument("--seed", default="ner-v50-background-performance-v1")
    args = parser.parse_args()
    rows = [json.loads(line) for line in args.fixture.read_text().splitlines() if line]
    ids = select(rows, args.size, args.seed)
    args.output.write_text(json.dumps({"seed": args.seed, "fixture_ids": ids}, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
