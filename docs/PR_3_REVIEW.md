# PR #3 merge review

Baseline reviewed on **26 October 2026**. The original findings and evidence are
preserved below. The six fixes and their verification are recorded in the
[resolution section](#resolution-on-27-october-2026).

## Review scope and verdict

- Merge: `d55c10414ef27ea4dae572d09fc38e2f100615e4`.
- Comparison base: first parent `6030ba5`.
- Merged branch tip: `5843ffc` (`local/ner-changes`).
- Scope: the full merge, covering 338 changed files, rather than only the final backstop-worker commit.
- Pull request: [IMshub10/MsgSense #3](https://github.com/IMshub10/MsgSense/pull/3).

The missing native tokenizer is a release blocker for ordinary builds from a
checkout without manually generated JNI libraries. The review also identified a
backfill interruption bug and four banking correctness or usability issues.

## Findings at the reviewed merge

### F1 — P1: normal builds omit the required tokenizer library

**Location:** [ner/build.gradle.kts](../ner/build.gradle.kts), lines 59–68,
`buildRustTokenizerAndroid` task registration.

The task is registered but is not connected to Android variant packaging.
Its output, `ner/src/main/jniLibs/`, is gitignored. A normal build can therefore
succeed without the library required by `HfTokenizerBridge.loadLibrary()`.

**Evidence:** `:app:assembleDebug` succeeded. Inspection of the resulting APK
found the NER model and ONNX native libraries for the configured ABIs, but no
`libhf_tokenizer_jni.so`. Extraction will fail at `System.loadLibrary` unless the
native build has been run manually first.

**Fix direction:** connect native generation to variant packaging, preferably as
a generated JNI source directory, and fail the build if a required ABI is missing.

**Acceptance:** assemble from a checkout without pre-generated JNI output;
inspect the APK for the tokenizer library in every supported ABI and run an
actual extraction on a device or emulator.

### F2 — P1: realtime preemption leaves backfill without a continuation

**Location:** [NerCoordinatorWorker.kt](../ner/src/main/java/com/summer/ner/NerCoordinatorWorker.kt),
lines 330–336, `processBackfill()` cleanup.

When realtime work is present, backfill exits and schedules a realtime worker
instead of its own continuation. The realtime path only schedules realtime
successors, including an eventual worker that finds its queue empty. Nothing
then promptly resumes the pending historical rows.

**Impact:** a normal incoming transaction can interrupt the historical drain
until another external trigger, potentially the next daily backstop.

**Fix direction:** make the realtime-to-backfill handoff explicit when realtime
work drains, while preserving one serialized backfill lane. Avoid relying on
screen entry or the daily sweep for normal progress.

**Acceptance:** interrupt a nonempty backfill queue with realtime work; verify
backfill resumes after realtime success or terminal failure without another
external trigger. Also cover worker cancellation and duplicate triggers.

### F3 — P2: merge and reassignment never receive account choices

**Location:** [AccountDetailViewModel.kt](../app/src/main/java/com/summer/notifai/ui/banking/account/AccountDetailViewModel.kt),
lines 22–23, `allAccounts`.

`allAccounts` uses `SharingStarted.WhileSubscribed`, but the fragment never
collects it. Merge and assignment dialogs only read `.value`. With no subscriber,
the upstream flow does not start and the value remains the initial empty list.

**Impact:** Merge silently returns; reassignment offers only Unassign even when
valid target accounts exist.

**Fix direction:** collect account choices with the fragment lifecycle or load
them explicitly when opening the dialog.

**Acceptance:** seed compatible accounts, open the actual account screen, and
verify both dialogs expose valid targets after database changes and lifecycle
stop/start.

### F4 — P2: new transactions return to hidden merged accounts

**Location:** [BankAccountOrganizer.kt](../ner/src/main/java/com/summer/ner/banking/BankAccountOrganizer.kt),
lines 42–45, existing-account lookup.

After A is merged into B, fingerprint lookup still returns A. The organizer
uses its ID without following `merge_target_id`, even though account observation
excludes merged accounts.

**Evidence:** an in-memory check using the exported schema and lookup query
returned account A after A→B, while the visible-account query returned only B.

**Impact:** new transactions and balance observations for A are linked to a
hidden account and disappear from visible account histories.

**Fix direction:** resolve the surviving merge target before creating links or
balance observations. Preserve the original fingerprint as an identity alias.

**Acceptance:** merge A→B, ingest a new transaction for A, and verify its history
and latest balance appear under B. Cover successive merges A→B→C as well.

### F5 — P2: custom ranges exclude most of the final selected date

**Location:** [BankingHomeViewModel.kt](../app/src/main/java/com/summer/notifai/ui/banking/home/BankingHomeViewModel.kt),
lines 39–40, `setCustomRange()`.

MaterialDatePicker supplies selected dates at UTC midnight. Adding one
millisecond produces an exclusive endpoint immediately after midnight, rather
than after the selected final day. A same-day selection becomes a one-millisecond
interval, with additional calendar-date errors outside UTC.

**Fix direction:** interpret the picker's UTC date values as calendar dates,
then construct the intended local start-of-day and following-day exclusive end.
Use calendar operations rather than assuming every local day is 24 hours long.

**Acceptance:** cover a same-day range, a transaction late on the final day,
Asia/Kolkata, a timezone behind UTC, and a daylight-saving transition.

### F6 — P2: unparseable amounts bypass the review filter

**Location:** [BankingTransactionFactBuilder.kt](../core/src/main/java/com/summer/core/data/local/dao/BankingTransactionFactBuilder.kt),
lines 28–31, amount ambiguity validation.

The check tests whether an AMOUNT mention exists, rather than whether it
contains a valid normalized amount. The current normalizer also omits the pound
symbol from its currency-prefix expression.

**Evidence:** executing the compiled normalizer and fact builder with
`£100.00` and a valid DEBIT mention produced a null amount with review state
`AI_EXTRACTED`.

**Impact:** the transaction loses its amount and is excluded from totals without
appearing in Needs review.

**Fix direction:** validate the normalized numeric value, mark failed
normalization for review, and cover the supported currency symbols in parsing.

**Acceptance:** test valid INR/GBP values and an existing AMOUNT mention with a
null or invalid normalized value. Verify invalid amounts are excluded from
totals and visibly require review.

## Baseline validation performed

| Check | Result and limits |
|---|---|
| `:core:testDebugUnitTest`, `:ner:testDebugUnitTest`, `:app:testDebugUnitTest` | 49 tests passed: core 9, NER 3, app 37. |
| `python3 -m unittest discover -s scripts/tests -v` | Six benchmark-tool tests passed. |
| `:app:assembleDebug` | Passed; APK inspection exposed F1. Successful assembly does not establish working NER. |
| `:app:minifyReleaseWithR8` | Passed; mapping preserves the tokenizer JNI class name. Full release packaging/device behaviour was not established. |
| SQLite migration checks | Exported v2, v3, v4, and v5 schemas migrated to v6 with no checked column/index mismatches or foreign-key violations. A seeded v5 case preserved SMS, extraction, mentions, correction, account link, and balance rows. These were in-memory SQLite checks, not Android Room instrumentation tests. |
| Compiled amount normalization/fact projection | Reproduced F6 with `£100.00`. |
| Account lookup after merge | Reproduced F4 using an in-memory schema/query check. |
| `git diff --check HEAD^1 HEAD` | Passed. |

Android device instrumentation tests were not run. Standalone `:ner:assembleDebug`
was blocked by uncached lint dependencies in offline mode; this was an environment
limitation, not an established source defect.

Build/test checks used the working checkout, which already had edits to
`app/build.gradle.kts` (application ID) and `.idea/misc.xml`. Those edits were
preserved. Source review used the committed merge; build results are not a claim
of validation against a pristine checkout with the committed application ID.

## Relationship to the backend plan

The [financial backend plan](FINANCIAL_BACKEND_PLAN.md) changes the proposed
long-term ownership of extraction and analytics. It does not fix these findings.
F3–F6 remain relevant to UI or financial-domain behaviour after migration. F1–F2
must be addressed wherever the existing local NER pipeline remains active during
the transition.

## Resolution on 27 October 2026

Implementation branch: `codex/fix-banking-transactions`, based on reviewed merge
`d55c104`. Backend processing remains future work in the separate plan.

| Finding | Resolution | Regression evidence |
|---|---|---|
| F1 | Fixed. Every Android variant consumes generated JNI output under `ner/build/`; the retained manual task delegates to those variant tasks. Cargo uses a build-directory cache, and generation rejects a missing or empty library for either supported ABI. Legacy source-tree binaries are excluded. | Debug and release APK builds regenerated absent JNI output. APK library and release JNI-name checks are recorded below. |
| F2 | Fixed. A priority-specific pending query retains a serialized backfill continuation independently of realtime scheduling. Realtime preemption delays that continuation by 10 seconds; duration-cap continuation has no delay. Realtime priority is checked before binding inference. Continuations await durable WorkManager enqueue. | Actual coordinator/WorkManager tests cover realtime success and terminal failure, repeated external triggers, cancellation/recovery with the same extraction ID, and explicit cancellation without restarting a drain. |
| F3 | Fixed. Account choices start eagerly in the ViewModel scope. Merge navigation awaits the database write so leaving the screen cannot cancel it. | A real database/ViewModel test reads `.value` without collecting it and verifies updates. Android UI coverage opens merge and reassignment dialogs after lifecycle restart, assigns a transaction, and verifies a merge persists. |
| F4 | Fixed. Account links and balance writes resolve merge chains transactionally. Fingerprints stay on the original rows as aliases; missing, cyclic, or incompatible chains are rejected. Startup repair also redirects existing stranded links. | Database tests cover A→B→C, subsequent A transactions and balances under C, stale assignment targets, existing stranded links, and invalid chains. |
| F5 | Fixed. UTC picker dates become device-local calendar-day boundaries, with the day after the end date as the exclusive endpoint. | Same-day and multiple-day ranges in UTC, India, and Los Angeles, including 23- and 25-hour DST days and reversed selections. |
| F6 | Fixed. Pound prefixes normalize, and unparseable normalized amounts become null amounts with `NEEDS_REVIEW` and `INVALID_AMOUNT`. | Normalizer/fact-builder tests cover supported currencies and malformed values. Repair tests verify old invalid projections require review while corrected effective values survive. |

### Existing-data recovery and diagnostics

`NerStartupRecoveryWorker` uses unique WorkManager work and the versioned
`banking_repair_v1_completed` preference. It repairs active current-pipeline
failed banking extractions once per installation, resetting attempts and retaining
extraction IDs. The marker is committed only after the database repair and durable
backfill scheduling succeed. Tests cover repeat startup, scheduling failure,
cancellation, and marker-write failure. Once repaired, later terminal failures
retain the ordinary bounded retry behavior.

Recovery uses backfill and clears historical notification readiness, so it does
not post old purchase alerts. Completed runs, account assignments, and user
overrides are retained. Invalid old AI projections are marked for review without
rerunning completed extraction. No Room schema change or database reset is used.

Service failure codes survive the remote exception wrapper. Logcat records run
IDs and failure codes. Banking empty states now distinguish preparation, active
analysis, and failures using the existing processing summary; no developer log
viewer was added.

The dialog regression exposed an additional defect in the original merge action:
it launched a ViewModel-scoped write and immediately popped the screen, cancelling
that scope before persistence. The fix awaits a successful merge before navigation.

### Fix validation

| Check | Verified result |
|---|---|
| JVM suites (`:core`, `:ner`, `:app`, `:classifier` `testDebugUnitTest`) | 69 tests passed: core 11, NER 16, app 42; classifier has no JVM test sources. This includes real Room and WorkManager regression tests. |
| Python benchmark suite (`python3 -m unittest discover -s scripts/tests -v`) | Six tests passed. |
| Android instrumentation (`:core`, `:ner`, `:app` `connectedDebugAndroidTest`) | 12 tests passed on the API 36 arm64 Pixel 8 emulator: core 2, NER 3, app 7. Includes Android Room migration, repository tests, actual isolated-service native inference, persisted facts and visible transaction detail, secure list/detail/edit flow, failed-analysis messaging, and merge/reassignment dialogs after lifecycle restart. |
| Native generation and APK packaging | The manual Rust task and ordinary debug/release APK builds passed. Both variants regenerated deleted generated JNI output. Debug contains `libhf_tokenizer_jni.so` for arm64-v8a and x86_64; release contains it for its configured arm64-v8a ABI. Both contain the NER model. |
| Release shrinker/JNI compatibility | Release DEX retains `com.summer.ner.tokenizer.HfTokenizerBridge` and `nativeLoadTokenizer`, `nativeEncode`, and `nativeFree`; the packaged native exports have matching JNI names. Local release validation excludes `:app:uploadCrashlyticsMappingFileRelease`. |
| Data compatibility and source hygiene | No Room entity/schema version change, destructive migration, generated native binary, model asset, or local configuration is committed. `git diff --check` passes. |

Test setup issues found during validation were corrected: library test APKs now
include their configured Android test runner, synthetic account fixtures are
cleaned up, and UI matchers select visible tab content. The lifecycle test allows
the resumed window's input transition to settle before tapping Merge. The final
suite passes with the production merge-persistence fix described above.

The emulator contains synthetic test data only. Inference assertions inspect the
persisted amount and entity spans before fixture cleanup. Relevant Logcat shows
startup repair completing and no native tokenizer failure during the passing
inference test. This verifies the local pipeline, not an existing phone's data.

The pre-existing application-ID edit and `.idea/misc.xml` edit remain outside the
commits. APK and emulator validation use the committed production ID
`com.utilities.msgsense`, supplied by a temporary Gradle validation init script
without rewriting the local application-ID edit. The local ID
`com.summer.notifai` identifies a separate app and database and must be checked
when diagnosing an installed build.

No physical Android device is connected. Installed builds and data on the user's
phones therefore remain unverified. Diagnosis on those devices must check package
identity, packaged tokenizer libraries, extraction status counts and failure
codes, persisted facts, history visibility, and relevant Logcat without clearing
application data. The baseline missing tokenizer establishes a defect in locally
built APKs; it does not establish the cause on an uninspected installed APK.
