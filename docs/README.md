# Documentation

## Current review and proposed direction

- [PR #3 review](PR_3_REVIEW.md) — six open findings from merge `d55c104`, reproduction evidence, and validation limits.
- [Financial backend plan](FINANCIAL_BACKEND_PLAN.md) — proposed ownership of financial processing, SMS/email reconciliation, prompt alerts, manual entries, and migration stages.

The backend plan records the product discussion on 26 October 2026. It does not
describe an implemented backend or authorize uploading existing users' messages.
Existing local processing remains the current implementation until the migration
is built and enabled with explicit financial-sync consent.

## Existing implementation and research

- [Architecture and modularization](modularization-plan.md) — current module ownership and NER worker design.
- [Database schema](db-schema.md) — local persistence.
- [Banking roadmap](BANKING_ROADMAP.md) — existing local banking backlog.
- [Bank service registry](BANK_SERVICE_REGISTRY.md).
- [NER background benchmarks](NER_BACKGROUND_BENCHMARK.md).
- [NER v50 benchmark results](ner-v50-benchmark/README.md).
- [Earlier NER integration assessment](ner-integration/OUTCOME.md) — historical recommendations; some implementation details predate the module extraction.

The local-only requirements in earlier documents describe that implementation.
The new plan proposes an opt-in cloud mode and must not be interpreted as an
already-completed change to those requirements.
