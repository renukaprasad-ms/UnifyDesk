# B2 Integration Remediation Prompt — Platform Hardening R1

**Prepared only. Do not start unless L1/Main assigns this after SEC/QA or explicitly chooses to remediate the known finding first.**

Repository:

`renukaprasad-ms/UnifyDesk`

Known integrated production-code base:

`7fe25fe73eca72b57172d025d944b6ed54ce196f`

Primary finding already supported by executable worker evidence:

- email-provider Maven Wrapper bootstrap is fixed;
- B2 exact-head job `110276953077` reached Spotless;
- Spotless inspected 190 Java files and found 40 requiring formatting.

When assigned, L1 must freeze the exact remediation base SHA again before work starts.

## Scope

Own email-provider verification remediation only:

1. apply the existing Spotless formatter to `email-provider`;
2. do not disable/exclude formatting or change formatter rules;
3. keep changes mechanical unless executable tests expose a real defect;
4. execute:
   - `./mvnw -B spotless:check`
   - `./mvnw -B clean test`
   - `./mvnw -B package -DskipTests`
5. classify any post-format test/package failure before editing behavior.

Do not change backend application business logic.
Do not change frontend.
Do not alter website content.
Do not weaken the canonical `Checks` workflow.

## Deliverable

Open a targeted remediation PR and report exact base/head SHA plus actual command evidence. Do not merge independently.
