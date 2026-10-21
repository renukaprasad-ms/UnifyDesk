# Mail work queue

Updated: October 21, 2026. Work proceeds one item at a time, following
`MAIL_RELEASE_GATES_AND_IAM_PLAN.md`: finish core mail before expanding IAM.
Historical QA documents describe the evidence available on their audit date;
they are not current test results.

1. **Local build and unit verification (completed with platform skips).** Run
   `tests/mail-verify.sh`, fix formatting and executable failures, and record
   results. Java 25 and Node 24 are available on this Windows workstation.
   Spotless corrected 144 Java files. The scanner integrity test now checks
   the actual integrity error rather than depending on HTTP server scheduling.
   The durable spool success test requires Linux directory fsync and is
   explicitly Linux-only; production storage still fails closed on Windows.
2. **Real PostgreSQL migrations.** Run `MailPostgresMigrationTests` against a
   disposable PostgreSQL database: fresh V1-V22 and upgrades from V13, V16,
   V19 and V21. H2 unit tests do not establish migration correctness.
3. **Linux storage and inbound integration.** Run the durable-spool test on
   Linux, then R10 readiness, private API intake/idempotency, actual scanners
   and the isolated Postfix listener. Verify quota, ACL and failure recovery.
4. **Remaining core features.** Assess attachments, retention, backups/restore,
   orphan reconciliation and operational monitoring against source and tests;
   implement the first confirmed gap.
5. **IAM and release acceptance.** Complete identity/RBAC parity, MFA,
   recovery, delegation/revocation and audit requirements before GA, then
   obtain the remaining security, infrastructure and deliverability evidence.

No deployment or production readiness is implied by local verification.

## Local verification evidence (October 21)

- R10 offline pipe contract: passed.
- Spotless: passed after applying the configured formatter.
- Java 25 compilation and backend packaging: passed.
- Backend suite: 100 discovered, 98 passed, zero failures/errors, two skipped.
  The skipped checks are real PostgreSQL migrations (no disposable database
  supplied) and Linux directory-fsync durability (Windows workstation).
- Frontend production builds: passed with mail UI flags all off and all on.
  Added `frontend/package-lock.json` to pin the resolved dependency graph.
- Full backend output and the initial frontend installation failure are in
  `../mail-verification.log`. The npm failure came from a local shell path
  pointing to an OpenSSL directory; frontend verification passed after
  process-local `ComSpec` and `npm_config_script_shell` overrides.

Next item: disposable PostgreSQL migration verification, followed by the
Linux durability check. Neither has passed in this session.
