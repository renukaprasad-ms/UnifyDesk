# Production Readiness

Baseline: `dev@d9717f93b3e10a308da9636750a4df6926bc6016`

## Current disposition

UnifyDesk is **not a production release candidate** at this baseline.

The strongest area is product/source breadth: the repository has substantial multi-tenant application code, explicit IAM/entitlements, durable PostgreSQL models, Flyway migrations through V83, async/retry/DLT concepts, provider adapters, security controls, many focused tests, Actuator/Prometheus exposure and feature-specific QA history.

The blocking gap is release evidence and production operations: required CI execution has historically been unavailable in important rounds, both long-lived branches are unprotected, production infrastructure is single-node/non-durable in several places, artifact provenance/rollback is not established, backup/restore is not demonstrated, and live provider validation is incomplete.

## Release-stage assessment

### Developer-ready

Substantially developer-ready. Source structure, Dockerfiles, compose test dependencies, local k3s helper, migrations and feature docs exist. Before treating a commit as a shared development baseline, restore executable CI and run the standard backend/frontend/email-provider/website checks.

### Internal alpha

Required before internal alpha:

- current SHA: UNIT TEST EXECUTED and INTEGRATION TEST EXECUTED for applicable suites;
- MIGRATION TEST EXECUTED from clean database and representative upgrade snapshots through V83;
- resolve the unsafe Flyway default mismatch;
- explicit production-like cookie/CORS/proxy configuration in staging;
- provider sandbox credentials for the flows under test;
- actuator readiness/liveness used by staging probes;
- basic backup + restore rehearsal for PostgreSQL;
- close/supersede stale PRs that can confuse integration.

### Limited beta

In addition to alpha gates:

- immutable, traceable application image tags/digests built by CI;
- durable production-grade PostgreSQL, Redis and Kafka topology with backups/recovery;
- rolling/zero-or-controlled-downtime deployment and documented rollback;
- branch protection and required status checks;
- TLS termination and trusted-proxy behavior proven end-to-end;
- centralized logs/metrics/alerts with privacy/redaction review;
- provider webhook verification and replay/idempotency tests;
- live SES, MSG91/DLT, Meta WhatsApp, Google Ads/Meta Ads checks for enabled modules;
- controlled concurrency/load tests on Contacts imports, Broadcast, provider rate limits, notifications and async queues.

### Production release candidate

Requires one frozen integrated SHA with:

- all applicable unit/integration/migration tests executed;
- SEC/QA review against exactly that SHA;
- provider tests executed for every feature enabled in the candidate;
- rollback and database restore rehearsal;
- tagged immutable artifacts;
- staging soak plus capacity/load results;
- no open P0 release blockers;
- explicitly accepted P1 risks with owners and dates.

### Public production / GA

Requires production operational maturity beyond source validation: monitored SLOs, capacity/headroom, incident/on-call ownership, tested disaster recovery, provider/compliance approvals, credential rotation, audit retention/privacy controls, cost/abuse controls and repeatable release/rollback procedures.

## P0 release blockers

1. **Branch governance:** `dev` and `main` report unprotected with required status checks disabled.
2. **Executable verification:** historical critical QA runs had zero executed steps; no release can infer test success from them.
3. **Flyway safety:** `application.yaml` defaults `FLYWAY_BASELINE_ON_MIGRATE=true` while `.env.example` and Compose default false. Production omission can silently enable baselining of a non-empty unmanaged database.
4. **Data/event durability:** current K8s is single-instance PostgreSQL/Redis/Kafka; Redis and Kafka manifests do not mount persistent volumes, and Kafka replication is effectively one.
5. **Release artifact provenance:** app images are `messaging-backend:latest` / `messaging-frontend:latest`; backend uses `imagePullPolicy: Never`; no CI release/tag/deploy workflow establishes immutable provenance.
6. **Backup/restore and DR:** no repository evidence of a tested production PostgreSQL backup/restore procedure or complete dependency disaster-recovery process.
7. **Provider go-live:** production provider verification is incomplete for modules intended to launch.
8. **TLS/proxy production contract:** ingress manifests do not define TLS, and forwarded-header trust defaults false; the actual external TLS/proxy configuration must be documented and verified.

## P1 hardening

- replace TCP-only probes with actuator readiness/liveness;
- define replica strategy and graceful rolling behavior;
- establish resource/capacity measurements rather than fixed local values;
- centralize secrets and rotation instead of provisioning one generic Secret directly from a local `.env`;
- define observability dashboards/alerts for API, DB pool, Kafka lag/DLT, Redis, provider errors, Broadcast, notification and webhook failure;
- define log retention and PII/credential redaction validation;
- execute load/concurrency testing for high-volume data and sending paths;
- document DLT inspection/replay/poison-message operations;
- establish dependency upgrade/image vulnerability scanning and SBOM/provenance;
- separate staging and production configuration/contracts.

## P2 later improvements

- multi-region/advanced DR if business RTO/RPO requires it;
- autoscaling after real workload measurements;
- more advanced release strategies such as canary/blue-green;
- automated provider synthetic checks;
- formal SLO/error-budget program;
- automated documentation freshness checks tied to module status.

## Flyway decision

For production, automatic `baseline-on-migrate=true` is not an appropriate default. Baseline is a one-time migration-adoption mechanism, not a normal release behavior.

Follow-up engineering should make fail-closed behavior explicit. Until then, production environments must explicitly set `FLYWAY_BASELINE_ON_MIGRATE=false`. If a legacy database genuinely needs baselining, use a controlled, backed-up, schema-diffed and separately approved operation. This documentation round does not alter runtime behavior.
