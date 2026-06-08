#!/usr/bin/env python3
"""Generate model-specific ONNX parity goldens for the Android NER benchmark."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

MAX_LENGTH = 128


def entities(tokens: list[str], labels: list[str]) -> list[dict[str, object]]:
    result: list[dict[str, object]] = []
    start = -1
    kind = ""
    for index, label in enumerate(labels + ["O"]):
        if label.startswith("B-") or (
            label.startswith("I-") and kind and label[2:] != kind
        ) or label == "O":
            if start >= 0:
                result.append(
                    {
                        "type": kind,
                        "text": " ".join(tokens[start:index]),
                        "start": start,
                        "end": index - 1,
                    }
                )
                start, kind = -1, ""
        if label.startswith("B-"):
            start, kind = index, label[2:]
        elif label.startswith("I-") and start < 0:
            start, kind = index, label[2:]
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", required=True)
    parser.add_argument("--onnx", required=True)
    parser.add_argument("--fixture", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    tokenizer = AutoTokenizer.from_pretrained(args.model_dir)
    session = ort.InferenceSession(args.onnx, providers=["CPUExecutionProvider"])
    input_names = {item.name for item in session.get_inputs()}
    id2label = {
        int(k): v
        for k, v in json.loads((Path(args.model_dir) / "config.json").read_text())[
            "id2label"
        ].items()
    }

    with open(args.fixture, encoding="utf-8") as source, open(
        args.output, "w", encoding="utf-8"
    ) as output:
        for line in source:
            case = json.loads(line)
            encoded = tokenizer(
                case["tokens"],
                is_split_into_words=True,
                truncation=True,
                padding="max_length",
                max_length=MAX_LENGTH,
                return_tensors="np",
            )
            feeds = {
                name: encoded[name].astype(np.int64)
                for name in input_names
                if name in encoded
            }
            argmax = session.run(None, feeds)[0][0].argmax(axis=-1).tolist()
            word_ids = [
                -1 if value is None else value for value in encoded.word_ids(batch_index=0)
            ]
            word_labels: list[str] = []
            previous = -1
            for position, word_id in enumerate(word_ids):
                if word_id >= 0 and word_id != previous:
                    word_labels.append(id2label[argmax[position]])
                previous = word_id

            record = {
                "id": case["id"],
                "input_ids": encoded["input_ids"][0].tolist(),
                "attention_mask": encoded["attention_mask"][0].tolist(),
                "token_type_ids": encoded.get(
                    "token_type_ids", np.zeros((1, MAX_LENGTH), dtype=np.int64)
                )[0].tolist(),
                "word_ids": word_ids,
                "argmax": argmax,
                "word_labels": word_labels,
                "entities": entities(case["tokens"], word_labels),
            }
            output.write(json.dumps(record, separators=(",", ":")) + "\n")


if __name__ == "__main__":
    main()
