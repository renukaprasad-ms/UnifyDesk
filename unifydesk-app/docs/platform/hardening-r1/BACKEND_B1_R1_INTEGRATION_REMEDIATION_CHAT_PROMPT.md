# B1 Integration Remediation Prompt — Platform Hardening R1

**Prepared only. Do not start unless L1/Main assigns this after SEC/QA or explicitly chooses to remediate the known finding first.**

Repository:

`renukaprasad-ms/UnifyDesk`

Known integrated production-code base:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

Primary finding already supported by executable worker evidence:

- backend `spotless:check` is not green;
- B2 exact-head execution inspected 1869 Java files and reported 809 requiring formatting;
- B1 later reported 810 after adding its hardening files.

When assigned, L1 must freeze the exact remediation base SHA again before work starts.

## Scope

Own backend verification remediation only:

1. apply the repository's existing Spotless formatter to backend Java source/tests;
2. do not disable Spotless, exclude files, change formatter rules or hand-format around the gate;
3. keep formatting mechanically separate from semantic corrections where practical;
4. execute with deterministic PostgreSQL/Redis and `SNOWFLAKE_NODE_ID=0`:
   - `./mvnw -B spotless:check`
   - `./mvnw -B test`
   - `./mvnw -B clean test`
   - `./mvnw -B package -DskipTests`
5. after formatting, classify every remaining failure as implementation regression, stale test, environment defect or cascade;
6. make only targeted corrections justified by the existing current contracts.

Do not weaken production `SnowflakeIdGenerator` validation.
Do not modify frontend, website, email-provider or CI workflow unless L1 explicitly changes ownership.
Do not turn broad existing feature failures into redesign projects.

## Deliverable

Open a targeted remediation PR and report exact base/head SHA plus actual command evidence. Do not merge independently.
