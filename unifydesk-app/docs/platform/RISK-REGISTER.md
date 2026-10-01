# Platform Risk Register

Baseline: `dev@d9717f93b3e10a308da9636750a4df6926bc6016`

Priority meaning:

- **P0** — blocks production release candidate / GA.
- **P1** — material hardening required before broad production.
- **P2** — later operational/product maturity improvement.

| ID | Priority | Risk | Evidence / impact | Required follow-up |
|---|---|---|---|---|
| PLAT-001 | P0 | `dev` and `main` unprotected | GitHub branch metadata reports protected=false; required checks off | enable protection/required checks/review policy |
| PLAT-002 | P0 | Canonical CI executable proof remains unavailable | Final remediated production-code SHA is `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3`; GitHub hosted-runner allocation repeatedly produced `runner_id=0` / zero-step jobs on earlier exact-SHA and remediation PR runs. Main explicitly deferred the external runner-allocation issue for normal development. Zero-step jobs are not PASS. | **OPEN / BLOCKED EXTERNAL / DEFERRED.** Do not block normal product development solely on runner allocation, but do not treat production-release CI gates as satisfied. Re-establish exact-SHA executable evidence before production RC/GA. |
| PLAT-003 | P0 | Flyway fail-closed source remediation is merged; final integrated-SHA execution remains deferred | Final source still defaults `baseline-on-migrate` false in both `application.yaml` and `DatabaseMigrationConfig`; earlier B1 executable evidence observed `PlatformMigrationHardeningTests` 4/4 and config tests 3/3, including clean->V83, V79->V83, fail-closed unmanaged V1-equivalent schema and controlled explicit adoption. PR #231 did not modify Flyway/V83 hardening. Exact final SHA `5012fd426b4d2da50d5c8fe5c44cf3f15b5aebc3` has not received independent executable migration proof. | **OPEN — SOURCE REMEDIATED / INTEGRATED EXECUTION DEFERRED.** Preserve fail-closed defaults and obtain final-SHA migration evidence before production RC/GA. |
| PLAT-004 | P0 | PostgreSQL single-node availability | one Deployment + one PVC | production DB/managed HA design, backups, PITR/restore rehearsal |
| PLAT-005 | P0 | Redis state is single-node and ephemeral in K8s | no PVC; one pod | define reconstructability; persistent/managed/HA Redis as required |
| PLAT-006 | P0 | Kafka is single broker with ephemeral storage and RF=1 behavior | one broker/controller, no PVC | production Kafka/managed service, persistence, replication/min ISR |
| PLAT-007 | P0 | Mutable/untraceable application artifacts | `latest`, backend pull Never, local image import | CI registry build, immutable tags/digests, provenance |
| PLAT-008 | P0 | No tested rollback/DR path | no release deploy/restore workflow in repo | application rollback + DB restore/recovery drills |
| PLAT-009 | P0 | Provider go-live not fully verified | historical docs identify real SES/MSG91/Meta gaps; Ads/mailbox require live checks | provider-specific validation rounds before enabling features |
| PLAT-010 | P0 | TLS/proxy contract external to manifests | ingress has no TLS; forwarded-header trust default false | document termination path; test secure cookies, scheme/IP/WS behavior |
| PLAT-011 | P1 | K8s probes are TCP-only | actuator readiness/liveness exists but unused | switch to HTTP readiness/liveness/startup probes |
| PLAT-012 | P1 | Single application replica / Recreate strategies | backend/front/dependencies provide little rollout resilience | deployment/replica/graceful-shutdown design |
| PLAT-013 | P1 | Generic Secret created from local `.env` | `k8s.sh` creates `messaging-env` | external secret manager, least privilege, rotation |
| PLAT-014 | P1 | Observability not operationalized | Actuator/Prometheus endpoints exist; no repository dashboards/alerts | metrics/logs/traces dashboards and paging thresholds |
| PLAT-015 | P1 | Load/concurrency evidence incomplete | source has bounded workers but current performance matrix not executed | representative 100/1k/10k+ and provider concurrency tests |
| PLAT-016 | P1 | Kafka DLT operations not formalized | retry/DLT source exists | runbook for inspect/replay/poison isolation/alerts |
| PLAT-017 | P1 | Privacy/logging retention validation incomplete | webhook/audit/log paths exist | PII/secret redaction review + retention/access controls |
| PLAT-018 | P1 | Notification status documentation materially stale | Round-0 integration doc despite later implementation | update feature current-status doc in dedicated doc/hardening round |
| PLAT-019 | P1 | Managed Email/SMS current-status docs stale | docs stop before later merged rounds | refresh current-status summaries without rewriting historical QA |
| PLAT-020 | P1 | Current Contacts source newer than final QA freeze | latest import UX/savepoint commits postdate prior QA | fresh current-head SEC/QA + migration/load checks |
| PLAT-021 | P1 | Ad Center source breadth lacks one current release-evidence summary | many later Google/Meta changes | dedicated provider/SEC-QA current-head validation |
| PLAT-022 | P1 | Open Contacts PR #218 diverged from current dev | head ahead 3, behind 11; overlapping later merged fixes | inspect equivalence; close/supersede or rework intentionally |
| PLAT-023 | P1 | Open Contacts architecture PR #197 is obsolete | dev contains later architecture + implementation; PR 197 far diverged | close as superseded after approval |
| PLAT-024 | P1 | Branch clutter from completed Contacts rounds | 20+ Contacts doc/feature/fix/review branches remain | cleanup after confirming merge/equivalence |
| PLAT-025 | P2 | Release tags/changelog semantics not standardized | no baseline release process | conventional release tags + generated notes/change log |
| PLAT-026 | P2 | SBOM/vulnerability/provenance automation absent | Dockerfiles pin majors/minors but no supply-chain gate | scanner/SBOM/signing/provenance |
| PLAT-027 | P2 | Autoscaling/capacity automation absent | fixed resources/replicas | add only after measured workloads |
| PLAT-028 | P2 | Documentation freshness is manual | several status files stale | current-status header/check tooling |
| PLAT-029 | P1 | Backend Spotless baseline remains unverified formatting debt | Historical real execution found roughly 809–810 Java files requiring formatting. B1 remediation classified/fixed semantic regressions but did not fabricate a repository-wide formatter rewrite without an executable formatter environment. | **DEFERRED VERIFICATION / FORMATTING DEBT.** May coexist with normal product development under Main's decision; must be executed and remediated before production release gates rely on Spotless. |
| PLAT-030 | P1 | Email-provider Spotless baseline remains partially remediated and unverified | PR #232 integrated five exact mechanical formatter corrections recoverable from prior real Spotless output; the full 40-file baseline, clean tests and package are not currently proven on the remediated tree. | **SOURCE REMEDIATION PARTIAL / DEFERRED VERIFICATION / FORMATTING DEBT.** Complete when an executable Java 25 environment is available; do not claim the gate green before then. |

## Repository hygiene recommendation

Do not delete in this baseline round.

### Open PRs

- **#197** `docs(contacts): freeze Contacts & Audience Round-1 architecture` — recommended close as superseded. Later Contacts architecture, implementation, QA and remediation are already in `dev`.
- **#218** `fix(contacts): improve CSV import mapping and field coverage` — requires explicit reconciliation, not blind merge. Its head is based on older `dev`, is now behind current `dev`, and overlaps subsequent import UX/savepoint work. Close as superseded if equivalent functionality is confirmed; otherwise re-create the missing delta from current `dev` in a new intentional round.

### Remaining remote branches

Remote branch inventory at the baseline consists almost entirely of `dev`, `main`, and completed Contacts round branches, including architecture/manifest, B1/B2/F1, integration, remediation and QA branches.

Recommended cleanup after approval and final equivalence check:

- `docs/contacts-audience-architecture-r0`
- `docs/contacts-audience-architecture-r1`
- `docs/contacts-audience-qa-r1-manifest`
- `docs/contacts-audience-round1-manifest`
- `docs/contacts-audience-round2-final-qa-freeze`
- `docs/contacts-audience-round2-remediation`
- `feature/contacts-audience-integration-r1`
- `feature/contacts-audience-processing-r1`
- `feature/contacts-audience-ui-r1`
- `feature/contacts-core-r1`
- `fix/contacts-audience-integration-r2`
- `fix/contacts-audience-processing-r2`
- `fix/contacts-audience-ui-r2`
- `fix/contacts-core-r2`
- `fix/contacts-import-mapping-ux` after PR #218 disposition
- `fix/contacts-import-savepoints`
- `fix/contacts-import-ux`
- `fix/contacts-list-detail-null-company`
- `fix/contacts-round3-hardening`
- `fix/contacts-testcompile-regressions`
- `review/contacts-audience-security-qa-r1`
- `review/contacts-audience-security-qa-r2`

Keep `dev`, `main`, and this baseline branch while its PR is active.
