# Platform Decisions

Baseline decisions established on 2026-10-01 against `dev@d9717f93b3e10a308da9636750a4df6926bc6016`.

## D-001 — Implementation is the primary source of truth

Existing READMEs/status documents are advisory until verified against implementation and runtime configuration.

## D-002 — Platform documentation is the current entry point

`unifydesk-app/docs/platform/README.md` is the authoritative navigation/status layer. Feature documents remain authoritative for detailed contracts when they are not superseded by implementation.

## D-003 — Historical QA remains immutable evidence

Round-specific QA reports retain value for their named SHA. Their old status language must not be rewritten to make history appear current. New platform/current-status documents supersede status, not evidence.

## D-004 — Evidence labels are explicit

Use only the platform verification vocabulary. “PASS” may be used inside a test report only where a test actually executed or when a clearly named source-review finding is explicitly labelled as source-only; platform summaries should prefer the evidence labels.

Zero-step CI jobs are EXECUTION BLOCKED.

## D-005 — Release candidates are immutable SHAs

A moving `dev` branch is not a release candidate. QA and release review operate against exact integrated SHAs.

## D-006 — Workers are independent

B1/B2/F1 start from the same frozen base/contracts and must not depend on another worker finishing first.

## D-007 — Production Flyway startup must fail closed

Normal production startup should have `FLYWAY_BASELINE_ON_MIGRATE=false`. Baseline operations require separate approval and backup/schema validation.

The code default is not changed in this documentation round; this decision creates follow-up engineering work.

## D-008 — Current k3s manifests are a development/small-host baseline

They are not accepted as the final GA infrastructure architecture because PostgreSQL/Redis/Kafka are single-instance, Redis/Kafka lack manifest persistence, images are mutable/local, and rollout/rollback is not production-grade.

## D-009 — Provider source completion is not provider readiness

SES, MSG91/DLT, Meta WhatsApp, Google Ads, Meta Ads, Gmail/Microsoft and other external integrations require live evidence for the environment/account capabilities intended for release.

## D-010 — `dev` integrates; `main` releases

Target semantics are: feature work -> PR to protected `dev`; frozen verified candidate -> explicit release PR to protected `main`; tagged immutable artifacts -> production.

Current branch protection does not enforce this yet.

## D-011 — No feature implementation in the baseline PR

This round is documentation/baseline-only. Runtime defects and release gaps are recorded as follow-up work rather than silently fixed.
