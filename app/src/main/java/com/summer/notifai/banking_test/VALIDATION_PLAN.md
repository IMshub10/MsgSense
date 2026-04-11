# ALBERT-v49 Banking NER — Validation Plan

This document describes the complete validation strategy for the banking NER
(Named Entity Recognition) pipeline, from the original Python/PyTorch model all
the way to on-device Android inference. It covers every stage of the pipeline,
the Rust JNI tokenizer, the differences between JVM and ART (Android Runtime)
testing, and how to reproduce validation with your own golden test data.

---

## Table of contents

1. [End-to-end validation chain](#end-to-end-validation-chain)
2. [Pipeline overview](#pipeline-overview)
3. [Golden test data format](#golden-test-data-format)
4. [Stage 1 — Pre-tokenizer + sender cleaning](#stage-1--pre-tokenizer--sender-cleaning)
5. [Stage 2 — Subword tokenization (Rust HF Tokenizers via JNI)](#stage-2--subword-tokenization-rust-hf-tokenizers-via-jni)
6. [Stage 3 — ONNX model inference + argmax](#stage-3--onnx-model-inference--argmax)
7. [Stage 4 — Word-level labels + BIO entity grouping](#stage-4--word-level-labels--bio-entity-grouping)
8. [JVM unit tests (desktop)](#jvm-unit-tests-desktop)
9. [Android instrumented tests (on-device)](#android-instrumented-tests-on-device)
10. [Why we test on both JVM and Android](#why-we-test-on-both-jvm-and-android)
11. [Rust JNI tokenizer — build and loading](#rust-jni-tokenizer--build-and-loading)
12. [How to run your own validation](#how-to-run-your-own-validation)
13. [Files required on Android](#files-required-on-android)

---

## End-to-end validation chain

The validation proves equivalence across three runtimes in two steps:

```
Step 1 (Python — already done):
  PyTorch (safetensors)  ══════  ONNX (Python)
         ↕                           ↕
    Full prediction parity verified on real SMS messages
    across different senders from banking_transactions.json
    Result: 3569/3569 PASSED (argmax, word_labels, entities all identical)

Step 2 (Android — this plan):
  ONNX (Python golden_test.json)  ══════  ONNX (Kotlin/Android)
         ↕                                     ↕
    Compare Android pipeline output against golden reference
    at each stage independently, then end-to-end

Together:
  PyTorch ══ ONNX (Python) ══ ONNX (Android/JVM)
  ← Step 1 proved this →  ← Step 2 proves this →
```

If your Android/JVM output matches `golden_test.json`, it also matches the
original PyTorch model. The golden data is generated from ONNX Runtime (Python),
which has been verified to produce **identical** predictions to PyTorch.

### What was done in Step 1 (Python side)

1. Trained an ALBERT token-classification model on banking SMS data using PyTorch.
2. Exported the model to ONNX format using `torch.onnx.export()`.
3. Ran the full pipeline (pre-tokenize → tokenize → inference → BIO decode) on
   3569 real SMS messages using both the PyTorch model and the ONNX model.
4. Verified that `argmax`, `word_labels`, and `entities` are **identical** for
   all 3569 messages between PyTorch and ONNX.
5. Generated `golden_test.json` containing test cases with all intermediate and
   final outputs from the ONNX pipeline.

---

## Pipeline overview

The Python pipeline (`predict.py` + `bio_converter.py`) runs these stages:

```
Raw sender + body
  │
  ├─ Stage 1: Clean sender + regex pre-tokenize → word list
  │
  ├─ Stage 2: HF tokenizer (SentencePiece Unigram) → input_ids, attention_mask, word_ids
  │
  ├─ Stage 3: ONNX model forward pass → logits → argmax
  │
  └─ Stage 4: Collapse argmax to word-level labels via word_ids → BIO grouping → entities
```

On Android/JVM, we replicate **all four stages** and validate each one independently
before validating the full chain end-to-end.

---

## Golden test data format

`golden_test.json` contains test cases sampled from real banking SMS messages.
Every field was generated from ONNX Runtime (Python) and verified to match
PyTorch predictions exactly.

**The golden test JSON can be generated from either the Python PyTorch model or
the Python ONNX model — both produce identical results (verified).**

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

**To run your own validation**, generate a golden test JSON in this exact format
(from either PyTorch or ONNX Python) and place it at
`app/src/main/assets/banking_test/golden_test.json`. See
[How to run your own validation](#how-to-run-your-own-validation) for details.

---

## Stage 1 — Pre-tokenizer + sender cleaning

**Implementation**: `BankingPreTokenizer.kt`

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

**Implementation**: `HfTokenizerBridge.kt` (Kotlin JNI bridge) + `rust_tokenizer/src/lib.rs` (Rust)

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

### Cargo dependencies

```toml
[dependencies]
jni = "0.21"
tokenizers = { version = "0.21", default-features = false, features = ["progressbar", "fancy-regex"] }
```

The `fancy-regex` feature is required — without it the `tokenizers` crate fails
to compile with "One of the onig, or fancy-regex features must be enabled".

### Pass criteria

All three arrays (`input_ids`, `attention_mask`, `word_ids`) must match golden
values element by element, all 128 positions.

---

## Stage 3 — ONNX model inference + argmax

### What it does

1. Feed `input_ids` (shape `[1, 128]`, dtype `int64`) and `attention_mask`
   (shape `[1, 128]`, dtype `int64`) to the ONNX model.
2. Model outputs `logits` of shape `[1, 128, 25]` (25 BIO label classes).
3. Take `argmax` along the last dimension → `int[128]` of predicted class indices.

### ONNX model details

| Property | Value |
|----------|-------|
| File | `model.onnx` + `model.onnx.data` (both must be in same directory) |
| Input names | `input_ids`, `attention_mask` |
| Input shapes | `[1, 128]` each |
| Input dtype | `int64` |
| Output name | `logits` |
| Output shape | `[1, 128, 25]` |

### ONNX Runtime: JVM vs Android

- **JVM tests** use `com.microsoft.onnxruntime:onnxruntime` (the pure JVM artifact).
- **Android tests** use `com.microsoft.onnxruntime:onnxruntime-android` (AAR with
  native libs for ARM/x86).

Both use the **same underlying C++ ONNX Runtime engine**. The only difference is
packaging. CPU inference produces identical `float32` logits and therefore
identical `argmax` results. This was confirmed by running the same golden test
cases on both runtimes.

### External model data

`model.onnx` references its weights from `model.onnx.data` (ONNX external
initializers). Both files **must be in the same directory** at runtime. On Android,
assets are read-only and not filesystem-accessible by path, so both files must be
copied to a writable directory (e.g. `cacheDir`) before creating the ORT session.

### Label index mapping (from `config.json`)

```
 0 = O              7 = B-DIRECTION    14 = I-AMOUNT     21 = I-MERCHANT
 1 = B-ACCOUNT      8 = B-LIMIT        15 = I-BALANCE    22 = I-REF_ID
 2 = B-AMOUNT       9 = B-MERCHANT     16 = I-BANK       23 = I-TXN_TYPE
 3 = B-BALANCE     10 = B-REF_ID       17 = I-CARD_TYPE  24 = I-UPI_ID
 4 = B-BANK        11 = B-TXN_TYPE     18 = I-DATE
 5 = B-CARD_TYPE   12 = B-UPI_ID       19 = I-DIRECTION
 6 = B-DATE        13 = I-ACCOUNT      20 = I-LIMIT
```

### Pass criteria

`argmax` array must match golden `argmax` at all 128 positions.

---

## Stage 4 — Word-level labels + BIO entity grouping

**Implementation**: `BankingBioDecoder.kt`

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

---

## JVM unit tests (desktop)

**Location**: `app/src/test/java/com/summer/notifai/banking_test/`

These tests run on the desktop JVM (not on Android). They were used for **fast
iteration** during development — no device/emulator needed, runs in seconds.

| Test file | Stage | What it validates |
|-----------|-------|-------------------|
| `BankingPreTokenizerTest.kt` | 1 | Pre-tokenizer against golden `words` |
| `HfTokenizerBridgeTest.kt` | 2 | Rust tokenizer against golden `input_ids`, `attention_mask`, `word_ids` |
| `BankingOnnxArgmaxTest.kt` | 3 | ONNX inference against golden `argmax` |
| `BankingBioDecoderTest.kt` | 4 | BIO decoder against golden `word_labels` and `entities` |
| `BankingEndToEndTest.kt` | 1→4 | Full pipeline — each stage feeds its actual output to the next |

### How native libraries are loaded on the JVM

On the JVM, the Rust native library is compiled for the **host platform** (macOS
→ `.dylib`, Linux → `.so`) and loaded using `System.load(absolutePath)` via
`HfTokenizerBridge.loadLibraryFromPath()`. The test locates the library at
`rust_tokenizer/target/release/libhf_tokenizer_jni.dylib`.

This is different from Android, where `System.loadLibrary("hf_tokenizer_jni")`
loads the `.so` from the APK's `jniLibs/` directory. The `HfTokenizerBridge`
class provides both methods:
- `loadLibrary()` — for Android (uses `System.loadLibrary`)
- `loadLibraryFromPath(absolutePath)` — for JVM tests (uses `System.load`)

### Building the Rust library for JVM tests

```bash
cd app/src/main/java/com/summer/notifai/banking_test/rust_tokenizer
CARGO_TARGET_DIR=./target cargo build --release
```

This produces `target/release/libhf_tokenizer_jni.dylib` (macOS) or
`target/release/libhf_tokenizer_jni.so` (Linux).

### ONNX Runtime on JVM

JVM tests use `com.microsoft.onnxruntime:onnxruntime` (JVM artifact). The model
files are read directly from the filesystem at `src/main/assets/banking_test/`.

---

## Android instrumented tests (on-device)

**Location**: `app/src/androidTest/java/com/summer/notifai/banking_test/BankingNerInstrumentedTest.kt`

These tests run on a real Android device or emulator using the **Android Runtime
(ART)**. They validate the exact same pipeline but in the actual production
environment.

| Test method | Stage | What it validates |
|-------------|-------|-------------------|
| `stage1_preTokenizer_allCases()` | 1 | Pre-tokenizer on ART |
| `stage2_tokenizer_allCases()` | 2 | Rust tokenizer `.so` loaded from APK |
| `stage3_onnxArgmax_allCases()` | 3 | ONNX-Android inference |
| `stage4_bioDecoder_allCases()` | 4 | BIO decoder on ART |
| `endToEnd_fullPipeline_allCases()` | 1→4 | Full chained pipeline on ART |

### How to run

```bash
# All banking NER tests
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.summer.notifai.banking_test.BankingNerInstrumentedTest

# Just end-to-end
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.summer.notifai.banking_test.BankingNerInstrumentedTest#endToEnd_fullPipeline_allCases
```

Or via adb:
```bash
adb shell am instrument -w \
  -e class com.summer.notifai.banking_test.BankingNerInstrumentedTest \
  com.utilities.msgsense.test/androidx.test.runner.AndroidJUnitRunner
```

### How assets and native libraries are loaded on Android

- **Assets**: accessed via `InstrumentationRegistry.getInstrumentation().targetContext.assets`
- **ONNX model**: both `model.onnx` and `model.onnx.data` are copied from assets
  to `cacheDir` so that ORT can load them from a filesystem path
- **Rust `.so`**: loaded via `System.loadLibrary("hf_tokenizer_jni")` — Android's
  classloader finds it in the APK's `lib/<abi>/` directory (sourced from `jniLibs/`)
- **`tokenizer.json`**: copied from assets to `cacheDir` so the Rust library can
  read it from a filesystem path

### Logcat output

All test progress is logged with tag `BankingNER`. Filter with:
```bash
adb logcat -s BankingNER:D
```

---

## Why we test on both JVM and Android

Testing on **both** the JVM and Android is necessary because they are different
runtimes with different behaviors:

| Aspect | JVM (desktop) | ART (Android) |
|--------|---------------|---------------|
| **Runtime** | HotSpot / OpenJDK | Android Runtime (ART) |
| **Regex** | `Pattern.UNICODE_CHARACTER_CLASS` supported | **Not supported** — throws `IllegalArgumentException` |
| **Native lib format** | `.dylib` (macOS) / `.so` (Linux x86_64) | `.so` (ARM64 / x86_64 Android) |
| **Native lib loading** | `System.load(absolutePath)` | `System.loadLibrary(name)` from APK |
| **ONNX Runtime** | `onnxruntime` (JVM JAR) | `onnxruntime-android` (AAR with native libs) |
| **Asset access** | Direct filesystem paths | `Context.assets.open()` → copy to `cacheDir` |
| **Float arithmetic** | x86_64 SSE/AVX | ARM NEON |
| **Speed** | Fast iteration (seconds) | Slower (requires device/emulator) |
| **CI integration** | Easy | Requires device farm or emulator |

### Specific issues we discovered by testing on Android

1. **`UNICODE_CHARACTER_CLASS` crash**: The pre-tokenizer regex used
   `Pattern.UNICODE_CHARACTER_CLASS` which works perfectly on the JVM but throws
   on Android. Fixed by using `[\s\p{Z}]` instead of `\s` in the negated
   character class.

2. **Native library format**: The Rust library must be cross-compiled for Android
   ABIs (`aarch64-linux-android`, `x86_64-linux-android`) using `cargo-ndk`.
   The JVM tests use the host-native `.dylib`/`.so` instead.

3. **ONNX external data loading**: On Android, `model.onnx` and `model.onnx.data`
   cannot be loaded directly from assets — they must be copied to a writable
   directory first. On the JVM, they're just read from the filesystem.

**JVM tests are for fast development iteration. Android tests prove it works in
the actual production environment (ART, ARM, APK packaging).** Both must pass.

---

## Rust JNI tokenizer — build and loading

### Project structure

```
rust_tokenizer/
├── Cargo.toml          # Dependencies: jni 0.21, tokenizers 0.21
├── Cargo.lock
└── src/
    └── lib.rs          # Three JNI functions
```

### Building for host (JVM tests)

```bash
cd app/src/main/java/com/summer/notifai/banking_test/rust_tokenizer
CARGO_TARGET_DIR=./target cargo build --release
# Produces: target/release/libhf_tokenizer_jni.dylib (macOS)
```

### Building for Android (cross-compilation)

Prerequisites:
```bash
# Install cargo-ndk
cargo install cargo-ndk

# Add Android Rust targets
rustup target add aarch64-linux-android x86_64-linux-android
```

Build:
```bash
cd app/src/main/java/com/summer/notifai/banking_test/rust_tokenizer
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>
cargo ndk -t arm64-v8a -t x86_64 -o ../../../../../../jniLibs build --release
```

Or use the Gradle task:
```bash
./gradlew :app:buildRustTokenizerAndroid
```

This produces:
```
app/src/main/jniLibs/
├── arm64-v8a/libhf_tokenizer_jni.so
└── x86_64/libhf_tokenizer_jni.so
```

### Loading differences

| Environment | Method | What it loads |
|-------------|--------|---------------|
| JVM (unit tests) | `HfTokenizerBridge.loadLibraryFromPath(path)` → `System.load(absolutePath)` | `target/release/libhf_tokenizer_jni.dylib` |
| Android (device) | `HfTokenizerBridge.loadLibrary()` → `System.loadLibrary("hf_tokenizer_jni")` | `.so` from APK's `lib/<abi>/` |

### JNI function naming

The Rust function names follow JNI conventions. Note the `banking_1test` segment:
the underscore in the Java package name `banking_test` is escaped as `_1` in JNI:

```rust
Java_com_summer_notifai_banking_1test_HfTokenizerBridge_nativeLoadTokenizer
Java_com_summer_notifai_banking_1test_HfTokenizerBridge_nativeEncode
Java_com_summer_notifai_banking_1test_HfTokenizerBridge_nativeFree
```

---

## How to run your own validation

### 1. Generate golden test data

From your Python pipeline (PyTorch or ONNX), generate a JSON file with this
exact structure — an array of test case objects:

```json
[
  {
    "sender": "HDFCBK",
    "body": "Rs.698 spent on HDFC Bank Card x1234 at AMAZON on 02-Feb",
    "words": ["SenderAddressId", ":", "HDFCBK", "Body", ":", "Rs.698", ...],
    "input_ids": [2, 2660, 106, 27950, ...],
    "attention_mask": [1, 1, 1, ..., 0, 0, 0],
    "word_ids": [-1, 0, 0, 0, ..., -1, -1],
    "argmax": [0, 0, 0, 0, 0, 2, 7, ...],
    "word_labels": ["O", "O", "O", "O", "O", "B-AMOUNT", "B-DIRECTION", ...],
    "entities": {
      "AMOUNT": ["Rs.698"],
      "DIRECTION": ["spent"],
      "BANK": ["HDFC Bank"],
      ...
    }
  },
  ...
]
```

**Important:**
- `input_ids`, `attention_mask`, and `word_ids` must all be length **128**
  (padded/truncated)
- `argmax` must be length **128**
- `word_ids` uses `-1` for special tokens (`[CLS]`, `[SEP]`) and padding
- `entities` is a map from entity type to a list of entity text strings
- The golden data can come from either PyTorch or ONNX Python — both produce
  identical results

### 2. Place assets

Put your files in `app/src/main/assets/banking_test/`:

```
banking_test/
├── model.onnx              # ONNX graph
├── model.onnx.data         # External weights (must be alongside model.onnx)
├── tokenizer.json          # HuggingFace fast tokenizer definition
├── tokenizer_config.json   # Tokenizer settings
├── config.json             # Model config with id2label mapping
├── metadata.json           # Training metadata, label list
└── golden_test.json        # Your test data (format above)
```

### 3. Build the Rust tokenizer

```bash
# For JVM tests (host):
cd app/src/main/java/com/summer/notifai/banking_test/rust_tokenizer
CARGO_TARGET_DIR=./target cargo build --release

# For Android tests:
./gradlew :app:buildRustTokenizerAndroid
```

### 4. Run JVM tests

```bash
./gradlew :app:testDebugUnitTest --tests "com.summer.notifai.banking_test.*"
```

### 5. Run Android instrumented tests

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.summer.notifai.banking_test.BankingNerInstrumentedTest
```

### Validation order

Always validate in order: **Stage 1 → 2 → 3 → 4**. Each stage depends on the
previous one being correct. If Stage 2 fails, Stage 3 and 4 results are
meaningless even if the code for those stages is perfect.

```
┌─────────────────────────────────────────────────────────┐
│  Stage 1: Pre-tokenizer          MUST PASS FIRST        │
│  ↓                                                      │
│  Stage 2: Tokenizer + padding    MUST PASS SECOND       │
│  ↓                                                      │
│  Stage 3: ONNX argmax            MUST PASS THIRD        │
│  ↓                                                      │
│  Stage 4: Labels + entities      FINAL VALIDATION       │
│  ↓                                                      │
│  End-to-End: Full pipeline       PROVES EVERYTHING      │
└─────────────────────────────────────────────────────────┘
```

---

## Files required on Android

| File | Size | Purpose |
|------|------|---------|
| `model.onnx` | ~1.5 MB | ONNX graph (references model.onnx.data) |
| `model.onnx.data` | ~42 MB | Model weights (must be in same directory as model.onnx) |
| `tokenizer.json` | ~1.9 MB | Full tokenizer definition for Rust HF tokenizers |
| `tokenizer_config.json` | 390 B | Tokenizer settings (lowercase, special tokens) |
| `config.json` | 1.8 KB | `id2label` mapping (index → BIO label name) |
| `metadata.json` | 3.5 KB | `label_list` array, `max_length=128` |
| `golden_test.json` | variable | Reference test data for parity validation |
| `libhf_tokenizer_jni.so` | ~5.5 MB per ABI | Rust tokenizer (in `jniLibs/arm64-v8a/` and `jniLibs/x86_64/`) |

---

## Common failure modes (reference)

| Stage | Symptom | Likely cause |
|-------|---------|-------------|
| 1 | Extra token from no-break space | Regex `\s` not matching Unicode whitespace — use `[\s\p{Z}]` |
| 1 | `Rs.698` split into `["Rs", ".", "698"]` | Regex doesn't have `[.,]` joining alphanumeric parts |
| 2 | First/last IDs wrong | `[CLS]`/`[SEP]` not added or added twice |
| 2 | All IDs shifted by 1-2 | Missing or extra special tokens |
| 2 | Different subword splits | Tokenizer not in lowercase mode, or wrong `tokenizer.json` |
| 2 | `word_ids` values off | Not using `is_pretokenized` mode |
| 3 | Completely wrong predictions | `model.onnx.data` not in same folder as `model.onnx` |
| 3 | Input shape error | Not feeding `[1, 128]` or wrong dtype (must be `int64`, not `int32`) |
| 4 | Extra or missing labels | Using ALL subwords instead of only first per word |
| 4 | Wrong entity boundaries | `I-XXX` not checking that current entity type matches |
| 4 | Entity text has extra/missing spaces | Join logic differs from `" ".join(tokens)` |
| Android | `UNICODE_CHARACTER_CLASS flag not supported` | Use `[\s\p{Z}]` instead of `Pattern.UNICODE_CHARACTER_CLASS` |
| Android | `UnsatisfiedLinkError` for native lib | `.so` not in `jniLibs/<abi>/` or not cross-compiled for target ABI |
| Android | ONNX model load fails | Assets not copied to `cacheDir` before creating session |
