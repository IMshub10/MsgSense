# MsgSense — Architecture & Modularization Plan

**Single source of truth** for the module split and the NER pipeline's current
state. This replaces the earlier, contradictory planning docs (`ner-pipeline-a1-plan.md`
and `ner-integration/NER_PIPELINE_HYBRID.md`, now removed). The `ner-integration/*`
deep-dives and benchmark docs remain as historical rationale only.

Goal: split the monolithic `:app`/`:core` into four modules with clear
responsibilities. NER and Classifier become separate feature modules; everything
foundational (DB, network, etc.) lives in `:core`; `:app` holds only UI.

---

## 1. Target module structure

- **`:app`** — UI only: Activities, Fragments, ViewModels, adapters, databinding,
  UI models, app-level DI (WorkerFactory/Configuration), `MainActivity`, `App`.
- **`:core`** — foundational: **DB** (all entities + DAOs + `SmsDatabase` + migrations),
  network, data sources, domain interfaces, **DI contracts** (the inter-module seams),
  notifications, permissions, device, util, base UI, SMS receivers.
- **`:classifier`** — SMS classification: workers, batch/stream orchestration,
  classifier model + tokenizer, classification use cases.
- **`:ner`** — NER: inference service (`:ner` process), MobileBERT runtime, Rust
  tokenizer/JNI, coordinator + realtime/backfill workers, `NerScheduler` impl,
  bank-account organizing, NER read repository + use cases.

### Module graph (must stay acyclic)

```
:app ──► :classifier ──► :core
:app ──► :ner ─────────► :core
:app ──► :core
:core ──► (no feature deps)
```

**Load-bearing constraint:** the Room `@Database` must reference *every* entity/DAO
it owns (`SmsDao`, `NerDao`, `ContactDao`, `SmsNer*`, `BankAccount*`, ...), so the DB
**cannot** be split across feature modules without a dependency cycle.

> **The entire DB layer stays in `:core`.** `:classifier` and `:ner` get their DAOs
> injected from `:core`. Feature modules never depend on each other — only on `:core`.

---

## 2. Inter-module communication

Feature modules talk to each other **only through interfaces owned by `:core`**,
implemented in the feature module and wired by Hilt at the `:app` level. There is
**no `:classifier` → `:ner`** Gradle edge and no ID handoff beyond what the interface
exposes — compile-time they don't know about each other; at runtime DI supplies the impl.

| Contract (in `:core`) | Status | Implemented by | Callers |
|---|---|---|---|
| `NerScheduler` (`core.ner.NerContracts`) | **exists** | `:ner` `NerWorkScheduler` | `SmsProcessingWorker` (classification success), banking ViewModels (fragment entry), `ReadSmsBroadCastReceiver` (realtime incoming) |
| `SmsClassifier` | **to add** | `:classifier` `SmsClassifierModel` | `:core` `SmsInserter` (single-message classify), `:classifier` batch processor |

`SmsClassifier` seam to add:

```kotlin
// :core
interface SmsClassifier {
    suspend fun classify(rawAddress: String, body: String): SmsClassification
}
```

- `SmsInserter` stays in `:core`, depends on the **interface** → no `core → :classifier` edge.
- `SmsClassifierModel` (impl) moves to `:classifier`, Hilt-bound.

> **Open point (see §5.4):** interface-via-`:core` is the approach *for now*. We may
> revisit a better mechanism (e.g. DB-observe / event-carried state) later.

---

## 3. Current NER pipeline behavior (as-is)

This is what the code does **today** (post removal of the app-startup trigger).

### Triggers (external entry points)
- **Classification success** — `SmsProcessingWorker` → `nerScheduler.enqueueBackfill()`.
- **Banking screen entry** — `BankingHomeViewModel` / `TransactionListViewModel` → `enqueueBackfill()`.
- **Incoming banking SMS (realtime)** — `ReadSmsBroadCastReceiver` → `enqueueRealtime(sms)`.
- ~~App startup~~ — **removed**.

External `enqueueBackfill()` uses WorkManager `KEEP` (dedups into an in-progress drain);
the coordinator's self-continuation uses `APPEND_OR_REPLACE` (baton pass).

### Realtime path — *kept as-is*
- SHORT_SERVICE foreground service, one item per run, `REALTIME_TIMEOUT_MS` = 2 min.
- **Model lifecycle: load → compute → unload per request.** The runtime is created on
  first extract in `NerInferenceService` and released when the worker closes the client.

### Backfill path
- Single **drain-to-empty loop** (`enqueueMissingBackfill` scan → drain `PENDING`).
- **Chunk size removed**; the only bound is the `BACKFILL_MAX_RUN_MS` = 5 min soft cap.
- **One warm binding for the whole run:** the model stays loaded for up to ~5 min, then
  unloads (`client.close()`) before the next backfill worker starts.
- Preempts for realtime work; if `PENDING` remains at run end, hands off to a fresh
  backfill worker via a chain baton (0 delay).
- DATA_SYNC foreground service (long-running, like the classifier).

---

## 4. File migration map (for the split)

### → `:classifier` (`com.summer.classifier`)
- `core/ml/model/SmsClassifierModel.kt`, `SmsClassifierOutputModel.kt` (impl of `SmsClassifier`)
- `core/ml/tokenizer/WordPieceTokenizer.kt`, `core/ml/util/Constants.kt`
- `core/android/sms/processor/SmsBatchProcessor.kt`
- `core/android/sms/service/SmsProcessingWorker.kt` (+ deprecated `SmsProcessingService.kt`)
- classifier DI (`ModelModule`, classifier part of `SmsModule`), `SmsClassificationException.kt`
- **new** `ClassifySmsUseCase` (orchestration extracted from `SmsRepository`/`SmsBatchProcessor`)
- classifier assets (`.onnx` + vocab); deps `onnxruntime`, `opennlp` move out of `:core`

### → `:ner` (`com.summer.ner`)
- `app/ner/*` (`NerCoordinatorWorker`, `NerInferenceService`, `NerIpc`, `NerMobileBertRuntime`,
  `NerModels`, `NerModule`, `NerNormalizer`, `NerPreprocessor`, `NerResultNotificationWorker`,
  `NerServiceClient`, `NerWorkScheduler`) — `NerWorkScheduler` binds `NerScheduler`
- `app/banking_ner/*` + `rust_tokenizer/` + `jniLibs/`
- `app/banking/BankAccountOrganization*`, `core/banking/BankAccountOrganizer.kt`, `BankRegistry.kt`
- `core/data/repository/NerExtractionRepository.kt` + banking presentation models
- NER assets (MobileBERT `.onnx` + `tokenizer.json`); manifest `NerInferenceService` (`process=":ner"`)
- gradle tasks: `buildRustTokenizerAndroid`, `syncProductionNerAssets`, `verifyBankRegistry`

### Stays in `:core`
DB (all DAOs/entities/models, `SmsDatabase`, `DatabaseModule`); contracts (`NerContracts`
= `NerScheduler` + `NerConstants`; **new** `SmsClassifier`); data sources
(`ISmsContentProvider`, `SmsMapper`); receivers; generic processors (`SmsInserter` [uses
`SmsClassifier`], `SmsSender`); notifications/permissions/device/util/base-UI; slimmed
`SmsRepository`; DI entry points.

### Stays in `:app`
`ui/*`, `MainActivity`, `MainViewModel`, `App.kt`, app-level DI, databinding, `SendSmsService`.

---

## 5. Open points (deferred decisions)

1. **Hybrid drain strategy.** Keep the current single drain-to-empty loop for now (it can
   be paused/halted while we iterate). Later, move to a **hybrid**: drain-to-empty while the
   app is in the **foreground**, and WorkManager-driven chunked draining when in the
   **background / when needed**.
2. **Cron / periodic backup trigger.** Decide whether we want a periodic sweep as a safety
   net for missed / pre-existing / process-death cases. Currently **not** present.
3. **NER model eviction (backfill).** Today the model unloads at the end of each ~5-min
   backfill run. Consider a **~120s idle warm-hold** so consecutive backfill runs reuse the
   loaded model instead of reloading. (Realtime eviction stays load → compute → unload.)
4. **NER ↔ Classifier communication mechanism.** Interface-via-`:core` for now; explore a
   better approach later (e.g. DB-observe / event-carried state, or a shared event seam).

---

## 6. Phased execution (each phase compiles & is independently valuable)

- **Phase 1 — Contracts & core inversion (still one module).** Add `SmsClassifier`
  interface; point `SmsInserter` + `SmsBatchProcessor` at it. Extract `ClassifySmsUseCase`;
  remove classification from `ISmsRepository`. Validate — decoupled but not yet moved.
- **Phase 2 — Create `:ner`.** Move NER code/assets/tasks/manifest; wire DI; `:app → :ner`.
  Lower risk (NER already lives behind `NerScheduler`).
- **Phase 3 — Create `:classifier`.** Move ML + batch processor + workers + use case + assets;
  `:app → :classifier`; `:classifier → :core` (contracts).
- **Phase 4 — Slim `:core` & `:app`.** Drop moved code + unused deps (`onnxruntime`,
  `opennlp`); confirm `:app` = UI only; per-module build checks + tests.

### Validation
`./gradlew :core:assembleDebug :classifier:assembleDebug :ner:assembleDebug :app:assembleDebug`;
run unit tests; manually verify onboarding classification + NER backfill end-to-end, the
incoming-SMS realtime path (receiver → classify → notify → NER), and that the `:ner` process
actually spawns.
