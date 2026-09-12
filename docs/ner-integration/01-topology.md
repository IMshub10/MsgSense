# Decision 1 · Deployment topology

Should the wallet feature ship inside NotifAI or as a separate app?

## Approaches considered

### 1.1 · Single app, all-in-one (monolithic)

Add the NER model, tokenizer, and Expenses UI directly into the existing
`:app` module. No new Gradle modules. One APK, one Room DB, one bottom-nav
tab (or menu item) for Expenses.

**Pros**

- Fastest to prototype — no Gradle wiring.
- Zero new build-system complexity.
- Reuses everything already in place: `WorkManager`, ingestion service,
  notification channels, Crashlytics, migrations.

**Cons**

- The Rust JNI tokenizer, ONNX runtime bindings, and NER assets sit right
  next to unrelated UI code — no clear ownership boundary.
- Any change to one area may force test/build cycles on the others.
- Impossible to later ship the NER feature independently or defer its
  download — everything is in one blob.

**When it makes sense.** Only for a throwaway proof-of-concept.

### 1.2 · Single app, modularized

Same shipping shape as 1.1 (one APK, one DB, two bottom-nav tabs), but the
code is split into new Gradle modules:

```
:app                    ← composition root, DI graph, navigation
:core                   ← existing shared infra (DB, SMS ingestion)
:ml-classifier          ← extracted from core.ml
:ml-ner                 ← new; owns Rust JNI, tokenizer, ORT session
:feature-messages       ← existing UI
:feature-expenses       ← new UI for the wallet tab
```

**Pros**

- Reuses everything from 1.1, plus clean ownership boundaries.
- Single write path — no cross-process consistency issues. Category edits
  in Expenses immediately reflect in Messages.
- One Play listing, one permission grant, one default-SMS-app flow.
- `:ml-ner` can be maintained, tested, and benchmarked independently, but
  ships in the same APK.
- Naturally extends to a Dynamic Feature Module or product flavors later
  without a rewrite.

**Cons**

- APK grows by the NER model + Rust `.so` per ABI (5–20 MB depending on
  final asset packaging).
- Users who only want Messages still pay that download cost.
- Coupled release cadence between Messages and Expenses.

**When it makes sense.** The default choice for a v1 wallet feature in a
single-team codebase. Fast to ship without painting yourself into a corner.

### 1.3 · Two apps, shared DB via `ContentProvider`

NotifAI (default SMS handler) owns the Room DB and exposes a signed
`ContentProvider` (`content://com.summer.notifai.provider/transactions`)
protected by a `signature`-level custom permission. A separate Wallet app
reads through the provider and hosts the Expenses UI.

**Pros**

- Truly independent release cadences and Play Store listings.
- Users install only what they want.
- Clear separation for marketing / brand story.

**Cons — several are dealbreakers**

- **Android allows exactly one default SMS app.** The Wallet app cannot
  ingest SMS itself; it must either IPC to NotifAI for every read, or ask
  for `READ_SMS` as a non-default reader — which triggers Google Play's
  SMS/Call-Log permissions declaration and a narrow-scope approval.
- Both apps need the classification/NER pipeline OR you duplicate the SMS
  work and lose the "classifier as router" optimization.
- Cross-process observability of writes (edits, category overrides)
  requires `ContentProvider.notifyChange` + `ContentObserver` wiring.
- Two crash-report pipelines, two analytics setups, two ProGuard configs,
  two model update paths.

**When it makes sense.** Only when there's a strong product reason to split
(e.g., you want to sell Wallet as a paid tier). Otherwise the tax is not
worth it.

## Comparison

| Approach | Ship time | APK size (Messages user) | Play SMS-policy risk | Router (classifier→NER) | Long-term flexibility |
| --- | --- | --- | --- | --- | --- |
| 1.1 Monolithic | days | + NER assets | none | trivial | very low |
| **1.2 Modularized single app** | ~1 wk refactor | + NER assets | none | trivial | **high** |
| 1.3 Two apps + provider | 3–4 wks | base only | **high on 2nd app** | hard | high |

## Recommendation

**Approach 1.2 — Single app, modularized.**

- Fastest realistic path that doesn't cost future flexibility.
- The modular Gradle layout means a later move to a Play Dynamic Feature
  Module (on-demand delivery of `:ml-ner` + `:feature-expenses`) becomes a
  small follow-up, not a rewrite, if APK size becomes a real problem.
- Avoids every hazard in 1.3 — default-SMS-handler singleton, Play policy,
  dual pipelines.
- The classifier-as-router optimization stays trivial because both models
  live in the same process, sharing one `OrtEnvironment`.

Ship 1.2 first. Migrate toward a Dynamic Feature Module only when
acquisition data shows APK size is hurting install conversion. Only reach
for 1.3 if the product story explicitly demands two separate Play listings.
