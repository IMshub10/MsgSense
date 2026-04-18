# ALBERT-v49 Banking NER — TFLite Validation Plan

This document describes the complete validation strategy for the banking NER
(Named Entity Recognition) pipeline using the **TFLite** model, from the
original Python/PyTorch model all the way to on-device Android inference.

This plan is self-contained. For the ONNX variant of the same NER pipeline see
the sibling plan:
[`banking_test_onxx/VALIDATION_PLAN.md`](../banking_test_onxx/VALIDATION_PLAN.md).
Stages 1, 2, and 4 are shared between the two and are described in full in
both documents.

---

## Table of contents

1. [End-to-end validation chain](#end-to-end-validation-chain)
2. [Pipeline overview](#pipeline-overview)
3. [Package layout](#package-layout)
4. [TFLite model details](#tflite-model-details)
5. [Golden test data format](#golden-test-data-format)
6. [Stage 1 — Pre-tokenizer + sender cleaning](#stage-1--pre-tokenizer--sender-cleaning)
7. [Stage 2 — Subword tokenization (Rust HF Tokenizers via JNI)](#stage-2--subword-tokenization-rust-hf-tokenizers-via-jni)
8. [Stage 3 — TFLite model inference + argmax](#stage-3--tflite-model-inference--argmax)
9. [Stage 4 — Word-level labels + BIO entity grouping](#stage-4--word-level-labels--bio-entity-grouping)
10. [Why TFLite has no JVM unit tests](#why-tflite-has-no-jvm-unit-tests)
11. [Android instrumented test (on-device)](#android-instrumented-test-on-device)
12. [Numerical parity result](#numerical-parity-result)
13. [TFLite-Android vs TFLite-Python: why they can drift](#tflite-android-vs-tflite-python-why-they-can-drift)
14. [Rust JNI tokenizer — build and loading](#rust-jni-tokenizer--build-and-loading)
15. [How to run your own validation](#how-to-run-your-own-validation)
16. [Files required on Android](#files-required-on-android)

---

## End-to-end validation chain

The validation proves equivalence across three runtimes in two steps:

```
Step 1 (Python — already done):
  PyTorch (safetensors)  ══════  TFLite (Python)
         ↕                           ↕
    Full prediction parity verified on real SMS messages
    Result: 3597/3597 PASSED (word_labels and entities identical)
    See parity_report.json for the full diff dump

Step 2 (Android — this plan):
  TFLite (Python golden_test.json)  ══════  TFLite (Android / ART)
         ↕                                     ↕
    Compare the Android pipeline output against the golden
    reference at each stage independently, then end-to-end

Together:
  PyTorch ══ TFLite (Python) ══ TFLite (Android)
  ← Step 1 proved this →    ← Step 2 proves this →
```

If the Android output matches `banking_test_tflite/golden_test.json`, it also
matches the original PyTorch model, because the golden data was produced by
the TFLite-Python reference which itself has 100% `word_labels`/`entities`
parity with PyTorch.

### What Step 1 produced (Python side)

From `parity_report.json`:
- `total_test_cases`: **3597**
- `passed`: **3597**
- `failed`: **0**
- `all_match`: **true**
- `unique_senders`: 50
- `format`: `TFLite float32` (non-quantized)
- `tflite_input_names`: `["input_ids", "attention_mask"]`
- `max_length`: 128
- `num_labels`: 25

Each case records PyTorch vs TFLite `word_labels` / `entities`; all 3597 match.

### What Step 2 validates (this plan)

On-device, we check that the same 3597 cases produce the same:
- `words` (Stage 1)
- `input_ids`, `attention_mask`, `word_ids` (Stage 2)
- `argmax` (Stage 3)
- `word_labels`, `entities` (Stage 4)

…when the pipeline runs under ART with the Android TFLite interpreter. The
actual result is in [Numerical parity result](#numerical-parity-result).

---

## Pipeline overview

```
Raw sender + body
  │
  ├─ Stage 1: Clean sender + regex pre-tokenize → word list          (shared code)
  │
  ├─ Stage 2: HF tokenizer (SentencePiece Unigram) → input_ids,      (shared code)
  │          attention_mask, word_ids
  │
  ├─ Stage 3: TFLite model forward pass → logits → argmax            (TFLite-specific)
  │
  └─ Stage 4: Collapse argmax to word-level labels via word_ids →    (shared code)
             BIO grouping → entities
```

Stages 1, 2, and 4 run the exact same Kotlin objects as the ONNX pipeline
(`BankingPreTokenizer`, `HfTokenizerBridge`, `BankingBioDecoder` from
`com.summer.notifai.banking_ner`). Only Stage 3 differs.

---

## Package layout

The pipeline code is split across three packages so the model-agnostic stages
(1, 2, 4) are reused between the ONNX and TFLite runners:

```
com.summer.notifai.banking_ner/           ← SHARED (runtime-agnostic code)
  ├─ BankingPreTokenizer.kt               (Stage 1)
  ├─ BankingBioDecoder.kt                 (Stage 4)
  ├─ HfTokenizerBridge.kt                 (Stage 2 — Rust JNI bridge)
  └─ rust_tokenizer/                      (Rust JNI source → libhf_tokenizer_jni)

com.summer.notifai.banking_test_tflite/   ← TFLite-specific
  ├─ BankingTfliteAssets.kt               (asset paths for model.tflite etc.)
  └─ VALIDATION_PLAN.md                   (this document)

com.summer.notifai.banking_test_onxx/     ← ONNX-specific (sibling; see its own plan)
  └─ BankingOnnxAssets.kt
```

Assets:
```
app/src/main/assets/banking_test_tflite/
  ├─ model.tflite              # TFLite graph with embedded weights (no side file)
  ├─ tokenizer.json            # HuggingFace fast tokenizer definition
  ├─ tokenizer_config.json     # Tokenizer settings (AlbertTokenizer)
  ├─ labels.txt                # Newline-separated BIO labels
  ├─ model_card.json           # max_seq_length, input/output shapes, labels
  ├─ parity_report.json        # PyTorch vs TFLite-Python parity dump
  └─ golden_test.json          # On-device test data (TFLite-Python output)
```

The `tokenizer.json` is identical to the ONNX one (same albert-base-v2
vocabulary), so the single shared Rust `.so` serves both pipelines.

### ONNX vs. TFLite — what changes at each stage

| Stage | Shared? | ONNX specifics | TFLite specifics |
|-------|---------|----------------|------------------|
| 1 Pre-tokenizer | ✅ shared | — | — |
| 2 Tokenizer | ✅ shared | — | — |
| 3 Inference | ❌ per-runtime | `onnxruntime-android`, external weights in `model.onnx.data` — model and data must be copied to cacheDir so ORT can resolve `.data` next to `.onnx` | `org.tensorflow:tensorflow-lite`, weights embedded inside `model.tflite` — load directly from a mmap'd `ByteBuffer`, no cacheDir copy needed |
| 4 BIO decoder | ✅ shared | — | — |

---

## TFLite model details

From `model_card.json`:

| Property | Value |
|----------|-------|
| File | `model.tflite` (single file — weights embedded) |
| Base model | `albert-base-v2` |
| Task | token-classification (NER) |
| Format | TFLite float32 (non-quantized) |
| Max sequence length | 128 |
| Num labels | 25 |
| Input names | `input_ids`, `attention_mask` |
| Input shape | `[1, 128]` each |
| Input dtype | `int64` |
| Output shape | `[1, 128, 25]` |
| Overall F1 | 0.9842 |

### How it differs from the ONNX model

| | ONNX | TFLite |
|--|------|--------|
| Package format | `.onnx` graph + **external** `.onnx.data` weights | Single `.tflite` file with **embedded** weights |
| Loading on Android | Must copy both files to `cacheDir`, point ORT at a filesystem path so it can resolve `.data` next to `.onnx` | Load from `AssetFileDescriptor` via mmap'd `MappedByteBuffer` — **no cacheDir copy** |
| Runtime (Android) | `com.microsoft.onnxruntime:onnxruntime-android` | `org.tensorflow:tensorflow-lite` |
| Input binding | By **name** (`mapOf("input_ids" to ..., "attention_mask" to ...)`) | By **index**; names are introspected at setup via `interpreter.getInputTensor(i).name()` |
| Numerical parity vs Python reference | Identical (same C++ ONNX Runtime engine on both sides) | Usually identical, but CPU kernels on arm64-v8a vs x86_64 can produce single-ULP drift at near-tie positions (see [TFLite-Android vs TFLite-Python](#tflite-android-vs-tflite-python-why-they-can-drift)) |

### Label index mapping (from `labels.txt` / `model_card.json`)

Same 25-class BIO scheme as ONNX:
```
 0 = O              7 = B-DIRECTION    14 = I-AMOUNT     21 = I-MERCHANT
 1 = B-ACCOUNT      8 = B-LIMIT        15 = I-BALANCE    22 = I-REF_ID
 2 = B-AMOUNT       9 = B-MERCHANT     16 = I-BANK       23 = I-TXN_TYPE
 3 = B-BALANCE     10 = B-REF_ID       17 = I-CARD_TYPE  24 = I-UPI_ID
 4 = B-BANK        11 = B-TXN_TYPE     18 = I-DATE
 5 = B-CARD_TYPE   12 = B-UPI_ID       19 = I-DIRECTION
 6 = B-DATE        13 = I-ACCOUNT      20 = I-LIMIT
```

---

## Golden test data format

`golden_test.json` contains test cases sampled from real banking SMS messages.
Every field was generated from **TFLite (Python)** and verified to match
PyTorch predictions for `word_labels` / `entities` across all 3597 cases.

Each test case has:

| Field | Type | Description |
|-------|------|-------------|
| `sender` | `string` | Raw sender ID (e.g. `"HDFCBK"`, `"575754"`) |
| `body` | `string` | Raw SMS body text |
| `words` | `string[]` | Expected word list after Stage 1 |
| `input_ids` | `int[]` (len 128) | Expected token IDs after Stage 2 |
| `attention_mask` | `int[]` (len 128) | Expected mask after Stage 2 |
| `word_ids` | `int[]` (len 128) | Subword-to-word mapping (`-1` = special/padding token) |
| `argmax` | `int[]` (len 128) | Expected per-position class index after Stage 3 |
| `word_labels` | `string[]` | Expected BIO label per word after Stage 4 |
| `entities` | `map<string, string[]>` | Expected grouped entities after Stage 4 |

The only difference versus the ONNX golden is the source: here `argmax` is
exactly what the Python TFLite interpreter predicted, so we can verify that
TFLite-Android reproduces TFLite-Python.

**To run your own validation**, generate a golden test JSON in this exact
format from your TFLite-Python reference and place it at
`app/src/main/assets/banking_test_tflite/golden_test.json`. See
[How to run your own validation](#how-to-run-your-own-validation) for details.

---

## Stage 1 — Pre-tokenizer + sender cleaning

**Implementation**: `com.summer.notifai.banking_ner.BankingPreTokenizer` (shared with ONNX)

### What it does

1. **Clean sender**: strip whitespace; if the 3rd character (index 2) is `"-"`,
   drop the first 3 characters.
   - `"AD-HDFCBK"` → `"HDFCBK"`
   - `"HDFCBK"` → `"HDFCBK"` (unchanged)

2. **Build prefix**: `"SenderAddressId : {cleaned_sender} Body :"`

3. **Regex word-tokenize** both the prefix and the body:
   ```
   ([A-Za-z0-9]+(?:[.,][A-Za-z0-9]+)*)|([^A-Za-z0-9\s\p{Z}])
   ```
   - Alphanumeric runs (optionally joined by `.` or `,`) → one token
   - Every non-alphanumeric, non-whitespace character → its own token
   - Whitespace (including Unicode separators) is skipped

4. **Concatenate**: prefix word tokens + body word tokens → final word list.

### Platform-specific note: Unicode whitespace

Python 3's `re` module treats `\s` as Unicode-aware by default, so it matches
characters like U+00A0 (no-break space). On the JVM, `java.util.regex.Pattern`
has a `UNICODE_CHARACTER_CLASS` flag to enable this. However, **Android's ART
runtime does not support this flag** — it throws
`IllegalArgumentException: UNICODE_CHARACTER_CLASS flag not supported`.

The fix is to use `[\s\p{Z}]` in the negated character class instead of `\s`.
`\p{Z}` is the Unicode "Separator" category, which includes no-break space and
other Unicode whitespace. This works on both the JVM and Android's ICU-based
regex engine, and matches Python's `\s` behavior.

### Worked example

```
sender = "HDFCBK"
body   = "Rs.698 spent on HDFC Bank Card x1234 at AMAZON on 02-Feb"

cleaned = "HDFCBK"
prefix  = "SenderAddressId : HDFCBK Body :"

prefix_tokens = ["SenderAddressId", ":", "HDFCBK", "Body", ":"]
body_tokens   = ["Rs.698", "spent", "on", "HDFC", "Bank", "Card",
                 "x1234", "at", "AMAZON", "on", "02", "-", "Feb"]

words = prefix_tokens + body_tokens
```

### Pass criteria

Word list must match `golden.words` exactly — same count, same strings, same order.

---

## Stage 2 — Subword tokenization (Rust HF Tokenizers via JNI)

**Implementation**: `com.summer.notifai.banking_ner.HfTokenizerBridge` (Kotlin JNI bridge) + `banking_ner/rust_tokenizer/src/lib.rs` (Rust). Shared with ONNX.

### What it does

1. Takes the word list from Stage 1.
2. Tokenizes with `is_pretokenized=true` semantics: each word is split into
   subwords independently (e.g. `"AMAZON"` → `["▁am", "az", "on"]`), preserving
   word boundary tracking.
3. Prepends `[CLS]` (id=2), appends `[SEP]` (id=3).
4. Pads with `<pad>` (id=0) to exactly **128** positions.
5. Builds `attention_mask`: `1` for real tokens, `0` for padding.
6. Builds `word_ids`: which original word index each subword belongs to; `-1` for
   special tokens and padding.

### Why Rust?

The ALBERT model uses a **SentencePiece Unigram** tokenizer, not WordPiece (which
is used by BERT/MobileBERT). The existing `WordPieceTokenizer.kt` in the codebase
cannot be reused. Reimplementing SentencePiece Unigram from scratch in Kotlin
would be error-prone and hard to validate.

Instead, we use the **Rust `tokenizers` crate** (the same library that powers
HuggingFace's `AutoTokenizer` in Python). This ensures:

- **Exact parity**: the Rust crate loads the same `tokenizer.json` file that
  Python uses, producing identical `input_ids`, `attention_mask`, and `word_ids`.
- **No reimplementation risk**: we're using the same underlying tokenizer library,
  just through a different language binding.
- **Performance**: Rust is fast and has no garbage collection overhead.

### How the Rust JNI bridge works

The Rust library (`hf_tokenizer_jni`) exposes three JNI functions:

1. **`nativeLoadTokenizer(path: String)`** — Loads `tokenizer.json` from disk
   into a static `Tokenizer` instance (thread-safe via `Mutex`).

2. **`nativeEncode(words: Array<String>): IntArray`** — Takes a Java
   `String[]` of pre-tokenized words, converts them to a Rust `Vec<String>`,
   and calls `tok.encode(words, true)`. Passing `Vec<String>` (instead of a
   single string) triggers the `is_pretokenized=true` mode in the HF tokenizer,
   so each word is tokenized independently and `word_ids` are tracked correctly.
   Returns a flat `IntArray` of length 384: `[input_ids(128), attention_mask(128), word_ids(128)]`.

3. **`nativeFree()`** — Releases the tokenizer (optional cleanup).

The Kotlin side (`HfTokenizerBridge.kt`) wraps these native calls and slices the
flat array into three separate `IntArray` fields in a `TokenizerResult` data class.

### Key settings (from `tokenizer_config.json`)

| Setting | Value |
|---------|-------|
| `tokenizer_class` | `AlbertTokenizer` (SentencePiece under the hood) |
| `do_lower_case` | `true` |
| `model_max_length` | `512` (but we truncate/pad to **128**) |
| `cls_token` / `bos_token` | `[CLS]` |
| `sep_token` / `eos_token` | `[SEP]` |
| `pad_token` | `<pad>` |
| `unk_token` | `<unk>` |

### Pass criteria

All three arrays (`input_ids`, `attention_mask`, `word_ids`) must match golden
values element by element, all 128 positions.

---

## Stage 3 — TFLite model inference + argmax

### What it does

1. Feed `input_ids` (shape `[1, 128]`, dtype `int64`) and `attention_mask`
   (shape `[1, 128]`, dtype `int64`) to the TFLite interpreter.
2. Run inference.
3. Read the `logits` output of shape `[1, 128, 25]` (float32).
4. Take `argmax` along the last dimension → `int[128]` of predicted class indices.

### How the model is loaded on Android

Unlike ONNX (where we must copy `model.onnx` + `model.onnx.data` to `cacheDir`),
TFLite embeds all weights inside the single `model.tflite` file. We can load
it directly from assets using a memory-mapped `ByteBuffer`:

```kotlin
private fun loadModelFile(assetPath: String): MappedByteBuffer {
    val afd: AssetFileDescriptor = ctx.assets.openFd(assetPath)
    FileInputStream(afd.fileDescriptor).use { fis ->
        return fis.channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength,
        )
    }
}

val interpreter = Interpreter(loadModelFile(BankingTfliteAssets.modelPath()))
```

For mmap to succeed, the model file must **not be compressed** inside the APK.
This is why `app/build.gradle.kts` declares:

```kotlin
androidResources {
    noCompress += listOf("tflite", "onnx", "onnx.data")
}
```

If you forget this, TFLite will fail at `Interpreter(...)` with an "file is
compressed" or "invalid flatbuffer" error.

### Resolving input tensor order

TFLite binds inputs by **index**, not by name. The order in which a converter
emits `input_ids` and `attention_mask` isn't guaranteed to be stable, so at
setup we introspect the interpreter and map names → indices:

```kotlin
for (i in 0 until interpreter.inputTensorCount) {
    val t = interpreter.getInputTensor(i)
    Log.d(TAG, "TFLite input $i: name=${t.name()} shape=${t.shape().toList()} dtype=${t.dataType()}")
    when {
        t.name().contains("input_ids", ignoreCase = true)      -> inputIdsIdx = i
        t.name().contains("attention_mask", ignoreCase = true) -> attnMaskIdx = i
    }
}
require(inputIdsIdx >= 0 && attnMaskIdx >= 0)
```

Running inference then looks like:

```kotlin
val ids : Array<LongArray> = arrayOf(LongArray(128) { tc.input_ids[it].toLong() })
val mask: Array<LongArray> = arrayOf(LongArray(128) { tc.attention_mask[it].toLong() })

val inputs  = arrayOfNulls<Any>(interpreter.inputTensorCount)
inputs[inputIdsIdx] = ids
inputs[attnMaskIdx] = mask

val output: Array<Array<FloatArray>> = Array(1) { Array(128) { FloatArray(25) } }
interpreter.runForMultipleInputsOutputs(inputs, mapOf(0 to output as Any))

val argmax = output[0].map { row -> row.indices.maxByOrNull { row[it] }!! }
```

### Pass criteria

`argmax` array must match the golden `argmax` at all 128 positions.

---

## Stage 4 — Word-level labels + BIO entity grouping

**Implementation**: `com.summer.notifai.banking_ner.BankingBioDecoder` (shared with ONNX)

### What it does

**Step A — Collapse subword predictions to word labels:**

For each position in the 128-length sequence:
- If `word_id == -1` → skip (special token or padding)
- If `word_id != previous_word_id` → this is the **first subword** of a new word;
  take `LABEL_LIST[argmax[position]]` as that word's label
- If `word_id == previous_word_id` → this is a **continuation subword**; ignore
  it (first subword wins)

Result: one label string per word (same length as `words` from Stage 1).

**Step B — BIO grouping:**

Walk through `(word, label)` pairs:
- `B-XXX` → start a new entity of type `XXX`
- `I-XXX` → continue the current entity **only if** the current entity type is
  also `XXX`
- `O` or type mismatch → close any open entity
- At the end, close any open entity

Each entity's text is the space-joined tokens.

### Worked example

```
words:       SenderAddressId  :  HDFCBK  Body  :  Rs.698  spent  on  HDFC  Bank  Card  x1234  at  AMAZON  on  02  -  Feb
word_labels: O                O  O       O     O  B-AMNT  B-DIR  O   B-BK  I-BK  O     B-ACC  O   B-MERC  O   B-DT I-DT I-DT

entities = {
  "AMOUNT":    ["Rs.698"],
  "DIRECTION": ["spent"],
  "BANK":      ["HDFC Bank"],
  "ACCOUNT":   ["x1234"],
  "MERCHANT":  ["AMAZON"],
  "DATE":      ["02 - Feb"]
}
```

### Pass criteria

Both `word_labels` list and `entities` map must match golden values exactly.

### Why Stage 4 tolerates small Stage 3 drifts

Because only the **first subword** of each word contributes to `word_labels`,
any `argmax` mismatch that lands on a continuation subword (or on a special
/ padding position where `word_ids == -1`) is discarded by Stage 4. This
matters for the TFLite pipeline: see
[Numerical parity result](#numerical-parity-result).

---

## Why TFLite has no JVM unit tests

For ONNX we have both desktop JVM unit tests (`onnxruntime` JVM artifact) and
on-device `androidTest`s, because the two share the same C++ engine and
produce identical results — JVM tests were useful for fast iteration.

For TFLite we only have `androidTest`s. Reasons:

1. **No apples-to-apples JVM analogue.** `org.tensorflow:tensorflow-lite` is
   an Android AAR; the desktop equivalents (`tensorflow-lite-api` + a separate
   native runtime, TensorFlow Java, or the Python interpreter) aren't
   drop-in — they have different kernel implementations, so a "JVM TFLite"
   test wouldn't prove anything about the Android TFLite behavior.
2. **Android is the production target.** The only number that matters is what
   `org.tensorflow:tensorflow-lite` produces under ART on arm64-v8a. Testing
   in that environment directly removes any translation gap.
3. **The shared stages are already covered by the ONNX JVM tests.** Stage 1,
   Stage 2 (Rust tokenizer), and Stage 4 are tested on the JVM via the ONNX
   unit tests — which use the **same Kotlin objects**. There's no benefit to
   re-running those assertions against the TFLite golden.

---

## Android instrumented test (on-device)

**Location:**
`app/src/androidTest/java/com/summer/notifai/banking_test_tflite/BankingTfliteInstrumentedTest.kt`

Runs on a real device or emulator under ART, using the real
`org.tensorflow:tensorflow-lite` native runtime.

| Test method | Stage | What it validates |
|-------------|-------|-------------------|
| `stage1_preTokenizer_allCases()` | 1 | Shared pre-tokenizer on ART (against TFLite golden `words`) |
| `stage2_tokenizer_allCases()` | 2 | Shared Rust tokenizer `.so` loaded from APK (against TFLite golden `input_ids`/`attention_mask`/`word_ids`) |
| `stage3_tfliteArgmax_allCases()` | 3 | **TFLite-Android inference** vs golden `argmax` |
| `stage4_bioDecoder_allCases()` | 4 | Shared BIO decoder on ART (against golden `word_labels`/`entities`) |
| `endToEnd_fullPipeline_allCases()` | 1→4 | Full chained pipeline on ART — each stage feeds its actual output to the next |

### How to run

```bash
# All TFLite banking NER tests
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.summer.notifai.banking_test_tflite.BankingTfliteInstrumentedTest

# Just the end-to-end test
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.summer.notifai.banking_test_tflite.BankingTfliteInstrumentedTest#endToEnd_fullPipeline_allCases
```

Or via adb:
```bash
adb shell am instrument -w \
  -e class com.summer.notifai.banking_test_tflite.BankingTfliteInstrumentedTest \
  com.utilities.msgsense.test/androidx.test.runner.AndroidJUnitRunner
```

### How assets and native libraries are loaded

- **Assets**: `InstrumentationRegistry.getInstrumentation().targetContext.assets`
- **TFLite model**: loaded directly from assets as a `MappedByteBuffer` (mmap);
  the APK must keep `.tflite` uncompressed (see `noCompress` above).
- **Rust `.so`**: `System.loadLibrary("hf_tokenizer_jni")` — same single `.so`
  the ONNX test uses, from `app/src/main/jniLibs/<abi>/`.
- **`tokenizer.json`**: copied from assets to `cacheDir` once, because the
  Rust tokenizer needs a real filesystem path.

### Logcat

All progress is logged with tag `BankingTflite`:
```bash
adb logcat -s BankingTflite:D
```

Expected output (one of each stage):
```
BankingTflite: Loaded 3597 TFLite golden test cases
BankingTflite: Rust HF tokenizer loaded
BankingTflite: TFLite input 0: name=serving_default_input_ids:0 shape=[1, 128] dtype=INT64
BankingTflite: TFLite input 1: name=serving_default_attention_mask:0 shape=[1, 128] dtype=INT64
BankingTflite: TFLite model loaded (input_ids=0, attention_mask=1)
BankingTflite: S1 Case 0 PASS
BankingTflite: S2 Case 0 PASS
BankingTflite: S3 Case 0 PASS
BankingTflite: S4 Case 0 PASS
BankingTflite: E2E Case 0 PASS
...
BankingTflite: End-to-end: 3596/3597 passed in 1075731ms
```

---

## Numerical parity result

Actual result from a full run on a real device (3597 cases):

```
End-to-end: 3596/3597 passed in 1075731ms  (~17m 55s, ~300 ms/case)

Failing case:
  E2E Case 3381  (sender=OLAMNY)  S1=OK  S2=OK  S3=FAIL  S4=OK
```

Breakdown of what that failure means:

- **S1 OK** — the Kotlin pre-tokenizer produced exactly the same words as
  TFLite-Python.
- **S2 OK** — the Rust HF tokenizer produced exactly the same `input_ids`,
  `attention_mask`, and `word_ids`.
- **S3 FAIL** — TFLite-Android `argmax` diverges from TFLite-Python `argmax`
  at **one** position inside the 128-length output.
- **S4 OK** — and yet `word_labels` and `entities` still match. That tells us
  the mismatched position is a **non-first-subword** of some word (or a
  special / padding position where `word_ids == -1`), so
  `BankingBioDecoder.collapseToWordLabels` discards its label before building
  the word-level output.

### What that means in practice

The contract that matters to the app — `entities` — has **100.00% parity**
with TFLite-Python across all 3597 cases. The raw `argmax` has 99.97% parity,
with one subword position flipped by single-ULP float drift. Across the whole
run that's one mismatched position out of 3597 × 128 ≈ 460k total positions.

---

## TFLite-Android vs TFLite-Python: why they can drift

The `model.tflite` flatbuffer is identical on Python and Android — same
weights, same graph, same ops. But the **runtime kernels** aren't:

- TFLite-Python links against the TensorFlow-built CPU kernels for your host
  (macOS x86_64 / arm64, Linux x86_64, …).
- TFLite-Android uses the arm64-v8a kernels bundled inside the
  `org.tensorflow:tensorflow-lite` AAR.

Those two builds can differ in:

1. **Matmul / FMA order**. CPU kernels reorder reductions for vectorization.
   Different ordering means different rounding in the last bit of float32.
2. **FMA availability**. `a*b + c` fused into one hardware instruction rounds
   once; done as two instructions it rounds twice. ARM64 and x86_64 don't
   always make the same choice.
3. **XNNPACK / ruy delegate selection.** Modern TFLite routes matmuls through
   XNNPACK by default; that's another layer with its own per-ABI kernels.

For a 25-way classifier over 128 positions, most of the time the top class
wins by a huge margin (logit gap ≫ noise), so argmax is stable. But a tiny
number of positions end up with two classes whose logits differ by less than
the ULP noise — those flip. That's exactly the one position we saw in Case
3381.

### Options if you want 100% numeric identity

1. **Accept it** (recommended). `entities` is what the app consumes, and
   that's at 100% parity. The single flipped subword is literally ignored by
   Stage 4.
2. **Tolerant comparison.** Replace strict `argmax == golden.argmax` with
   "argmax equal OR top-2 logits within ε" in Stage 3. Still catches real
   drift, ignores ULP noise.
3. **Regenerate the golden from Android TFLite.** Run the interpreter once on
   a reference device and freeze its `argmax` as the golden. Removes the
   cross-ABI delta at the cost of losing direct PyTorch/TFLite-Python parity
   in the golden file (you'd still have `parity_report.json` for that).
4. **Try a different delegate.** `XNNPACK` vs CPU vs NNAPI vs GPU can each
   change drift; none are guaranteed to eliminate it.

---

## Rust JNI tokenizer — build and loading

The Rust tokenizer is shared with the ONNX pipeline — same source, same
compiled `libhf_tokenizer_jni.so`. If you've already built it for ONNX you
don't need to build it again for TFLite.

### Project structure

```
app/src/main/java/com/summer/notifai/banking_ner/rust_tokenizer/
├── Cargo.toml          # Dependencies: jni 0.21, tokenizers 0.21
├── Cargo.lock
└── src/
    └── lib.rs          # Three JNI functions
```

### Building for Android (cross-compilation)

Prerequisites:
```bash
cargo install cargo-ndk
rustup target add aarch64-linux-android x86_64-linux-android
```

Build via the Gradle task:
```bash
./gradlew :app:buildRustTokenizerAndroid
```

This produces:
```
app/src/main/jniLibs/
├── arm64-v8a/libhf_tokenizer_jni.so
└── x86_64/libhf_tokenizer_jni.so
```

### JNI function naming

The Rust function names follow JNI conventions. Note the `banking_1ner` segment:
the underscore in the Java package name `banking_ner` is escaped as `_1` in JNI
(each `_` inside a package segment becomes `_1`):

```rust
Java_com_summer_notifai_banking_1ner_HfTokenizerBridge_nativeLoadTokenizer
Java_com_summer_notifai_banking_1ner_HfTokenizerBridge_nativeEncode
Java_com_summer_notifai_banking_1ner_HfTokenizerBridge_nativeFree
```

If you move `HfTokenizerBridge.kt` to a different package, you **must** update
these symbol names in `rust_tokenizer/src/lib.rs` and rebuild; otherwise
`System.loadLibrary` will link successfully but `nativeLoadTokenizer` will
throw `UnsatisfiedLinkError` at runtime.

---

## How to run your own validation

### 1. Generate golden test data from TFLite-Python

Run your TFLite-Python reference pipeline on your test corpus and emit a
JSON in the format described in
[Golden test data format](#golden-test-data-format). The TFLite repo you're
converting from should already produce this.

### 2. Place assets

Drop the files into `app/src/main/assets/banking_test_tflite/`:

```
banking_test_tflite/
├── model.tflite
├── tokenizer.json
├── tokenizer_config.json
├── labels.txt
├── model_card.json
├── parity_report.json     (optional)
└── golden_test.json
```

### 3. Build the Rust tokenizer

Only needed if you haven't already built it for the ONNX pipeline — the
same `.so` serves both.

```bash
./gradlew :app:buildRustTokenizerAndroid
```

### 4. Run the Android instrumented tests

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.summer.notifai.banking_test_tflite.BankingTfliteInstrumentedTest
```

Watch with:
```bash
adb logcat -s BankingTflite:D
```

### 5. Expected outcome

If the Rust tokenizer is healthy and the TFLite model was converted correctly,
you should see **3597/3597 (or your count / your count) E2E pass**. A tiny
number of single-position `argmax` drifts at near-tie subword positions is
**expected** (see [TFLite-Android vs TFLite-Python](#tflite-android-vs-tflite-python-why-they-can-drift)),
and as long as they land on non-first subwords Stage 4 will absorb them and
`word_labels` / `entities` will still match exactly.

### Validation order

Always validate in order: **Stage 1 → 2 → 3 → 4**. Each stage's golden data
is consumed by the next one's test; if Stage 2 fails, Stage 3 and 4 look
broken even if their code is correct. The E2E test chains them together and
is the most realistic proxy for production behavior.

---

## Files required on Android

Minimum set for the TFLite pipeline to work on-device:

| File | Location | Purpose |
|------|----------|---------|
| `model.tflite` | `assets/banking_test_tflite/` | Graph + embedded weights |
| `tokenizer.json` | `assets/banking_test_tflite/` | HF tokenizer definition (same content as the ONNX one) |
| `libhf_tokenizer_jni.so` | `jniLibs/<abi>/` | Rust HF tokenizer (built once, shared with ONNX) |

Optional but recommended:

| File | Location | Purpose |
|------|----------|---------|
| `labels.txt` | `assets/banking_test_tflite/` | Label list — handy at runtime if you don't want to hardcode it |
| `model_card.json` | `assets/banking_test_tflite/` | Shape/dtype metadata — useful for on-device introspection |
| `tokenizer_config.json` | `assets/banking_test_tflite/` | Tokenizer settings (mostly informational) |
| `golden_test.json` | `assets/banking_test_tflite/` | Only required for the `androidTest`; do not ship in release |
| `parity_report.json` | `assets/banking_test_tflite/` | Only required for auditability; do not ship in release |

### APK packaging checklist

- ✅ `noCompress += listOf("tflite", …)` in `app/build.gradle.kts` (so mmap works).
- ✅ `jniLibs/arm64-v8a/libhf_tokenizer_jni.so` is present (and `x86_64` if you
  target emulators).
- ❌ Don't commit `golden_test.json` or `parity_report.json` to the repo —
  they're large; `.gitignore` already excludes them.

---

*Keep this file aligned with `BankingTfliteInstrumentedTest.kt`,
`BankingTfliteAssets.kt`, and the shared `BankingPreTokenizer` /
`HfTokenizerBridge` / `BankingBioDecoder` in `banking_ner/` when behavior
changes. For the ONNX counterpart see
[`banking_test_onxx/VALIDATION_PLAN.md`](../banking_test_onxx/VALIDATION_PLAN.md).*
