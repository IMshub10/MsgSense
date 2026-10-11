# NER Integration Outcome

## Executive summary

The NER integration should stay inside the main NotifAI app. The docs make the
right architectural call: a second wallet app would add Android default-SMS-app,
Google Play SMS permission, IPC, and duplicated-pipeline risk without improving
the core user experience.

The current implementation is directionally strong. It already has a
classifier-gated NER queue, realtime and backfill workers, a separate NER
inference service, model-hash tracking, extraction status, retry state, and a
banking transaction UI layer. The main work now is to tighten lifecycle,
throughput, and modular boundaries so the implementation matches the performance
intent of the docs.

## What is already good

- The classifier remains the cheap router and NER only runs for banking
  transaction candidates.
- Live SMS uses the same high-level path as the docs require: classify, insert,
  enqueue realtime NER when eligible.
- NER state is durable in Room through `sms_ner_extractions` and
  `sms_ner_entities`, including attempts, status, model hash, tokenizer hash,
  preprocessing version, truncation, and inference latency.
- The extraction schema is better than the early docs' single
  `TransactionEntity` sketch because it keeps raw entity evidence and supports
  retry/recovery.
- MobileBERT v50 is the right production candidate based on the benchmark docs:
  best latency and throughput with acceptable diagnostic F1.
- The separate `:ner` process is a reasonable memory-isolation choice if kept
  deliberately documented.

## Main gaps against the docs

- The docs recommend NER lazy load plus held runtime with idle release. Current
  backfill closes the client after each chunk, and the service releases runtime
  on unbind. This risks repeatedly paying MobileBERT cold-load cost.
- Backfill currently processes small chunks and resumes after a quiet period.
  That protects realtime work but can make large historical sync much slower
  than the documented 10-15% overhead target.
- The docs describe a router-gated batch stage downstream of
  `SmsBatchProcessor`. Current code enqueues NER mostly after SMS processing
  completes, so NER does not overlap enough with classification/backfill.
- The project is still mostly `:app` + `:core`; the recommended `:ml-ner`,
  `:ml-classifier`, and banking feature module boundaries are not present yet.
- NER eligibility is hardcoded to classification id `2`. That may be correct
  for v1, but it needs to be named and tested as a product policy.
- There is no obvious `onTrimMemory` release hook for NER, despite the docs
  calling it out.

## Recommended next steps

1. Implement NER idle retention.
   Keep `NerMobileBertRuntime` alive across active realtime/backfill windows,
   release it after about 120 seconds idle, and release immediately on memory
   pressure.

2. Rework backfill draining.
   Remove the normal 30-second delay between chunks. Continue draining while no
   realtime work exists, and yield only for realtime priority, cancellation, or
   OS constraints.

3. Queue eligible NER work at batch-write time.
   After classified SMS batches are persisted, enqueue matching banking
   candidates in the same transactional write path so NER can start earlier and
   recover atomically.

4. Make transaction eligibility explicit.
   Replace raw `2` checks with a named policy. Start with only
   `BANKING_TRANSACTION`, then deliberately include or exclude rewards, balance
   updates, reversals, recharges, and OTP-linked transaction classes.

5. Keep the durable extraction model.
   Do not collapse the schema into a simple transaction table. Build transaction
   projections from extracted entities and user overrides, as the current code
   already does.

6. Modularize after lifecycle and throughput are stable.
   Extract `:ml-ner` first, then `:ml-classifier`, then banking UI/domain
   modules. Avoid doing this before the runtime behavior is corrected.

## Testing required before release

- Unit tests for NER eligibility policy.
- DAO tests for atomic SMS insert plus extraction enqueueing.
- Worker tests for realtime priority, backfill resume, retry, terminal failure,
  and notification suppression.
- Runtime lifecycle tests proving MobileBERT is reused across multiple requests
  and released after idle or memory pressure.
- Room migration tests for all NER and banking tables.
- Device benchmark pass for backfill wall time, first realtime NER latency,
  peak PSS, and recovery after worker/process interruption.

## Release guidance

Ship this only when the measured behavior matches the docs' intent:

- Non-banking SMS should pay classifier cost only.
- First banking SMS may pay a cold NER load.
- Subsequent banking SMS in the same activity window should reuse the loaded
  runtime.
- Backfill should not repeatedly cold-load MobileBERT.
- Banking notifications must stay private on the lock screen.
- All transaction data and summaries must remain local-only.
