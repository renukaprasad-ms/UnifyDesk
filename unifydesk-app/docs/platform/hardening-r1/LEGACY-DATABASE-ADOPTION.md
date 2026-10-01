# Legacy Database Adoption Runbook

Platform Hardening Round 1 defines Flyway baselining as an explicit one-time adoption operation, not a normal startup default.

## Normal behavior

`FLYWAY_BASELINE_ON_MIGRATE` defaults to `false`.

A non-empty schema without Flyway history must fail closed. Do not turn baselining on merely to bypass a migration error.

## Supported adoption case

The executable platform test proves one intentionally narrow case:

1. create a clean PostgreSQL database;
2. run the real UnifyDesk Flyway migration set only through V1;
3. remove only the Flyway history table, leaving the real V1 schema in place;
4. verify ordinary migration with baseline disabled refuses the unmanaged non-empty schema;
5. explicitly enable baselining with baseline version `1`;
6. migrate the known V1-compatible schema forward through current head V83;
7. run Hibernate schema validation against the migrated database.

This proves controlled adoption of a schema already known to be structurally equivalent to a specific UnifyDesk migration version. It does not prove that arbitrary legacy schemas are safe to baseline.

## Operator procedure

Before any production-like adoption:

1. stop application writes and take a verified database backup;
2. identify the exact UnifyDesk migration version the unmanaged schema is expected to match;
3. compare the actual schema to a database produced by the real migrations through that exact version;
4. investigate every schema difference before continuing;
5. record the selected baseline version and approval;
6. set `FLYWAY_BASELINE_ON_MIGRATE=true` and `FLYWAY_BASELINE_VERSION=<verified-version>` only for the controlled adoption;
7. run migration and verify the Flyway history contains the intended baseline entry and all later migrations;
8. run Hibernate schema validation and application smoke checks;
9. restore `FLYWAY_BASELINE_ON_MIGRATE=false` for normal operation.

If compatibility cannot be established, do not baseline. Restore from backup or create a purpose-built migration/remediation plan.
