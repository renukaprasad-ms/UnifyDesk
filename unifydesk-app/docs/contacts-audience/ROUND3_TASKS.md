# Contacts & Audience Round-3 Hardening

Status: IMPLEMENTED ON `fix/contacts-round3-hardening`

Base dev SHA:

`9cff9014d0b513fa1f1708d22987224a3f783cea`

## Scope

Round-3 closes the two Medium findings from final Round-2 QA and the backend compiler failures exposed by a real Docker/Maven build.

### CA-R2-001 — Bulk DELETE crash/checkpoint consistency

Implemented:

- destructive bulk deletes no longer run through the normal page-level mutation adapter;
- each Contact delete runs in a bounded transaction together with that Contact's durable bulk cursor/counter checkpoint;
- successful destructive state cannot commit without its corresponding checkpoint;
- a delete failure records a failed candidate without committing destructive state;
- the worker advances one Contact at a time for DELETE while preserving batched processing for non-destructive operations;
- focused worker coverage verifies DELETE is routed through the checkpoint coordinator rather than the generic mutation port.

### CA-R2-002 — Authorization-denied async resource state

Implemented:

- denied CONTACT_IMPORT claims terminalize the Contact import resource as FAILED;
- denied CONTACT_EXPORT claims terminalize the Contact export resource as FAILED;
- denied CONTACT_BULK claims terminalize the Contact bulk resource as FAILED;
- the async job is then marked FAILED with the stable authorization decision code;
- workers are not invoked after denial;
- focused tests cover import, export, and bulk denial paths.

## Compiler remediation

Fixed the build errors found by `./k8s.sh restart`:

- Apache Commons CSV 1.11 builders now use `.build()` rather than unsupported `.get()`;
- the `Decision(boolean allowed, ...)` record factory no longer collides with the generated `allowed()` accessor.

Affected compiler-fix files:

- `ContactExportWorker.java`
- `ContactImportService.java`
- `ContactImportWorker.java`
- `ContactProcessingAuthorizationPolicy.java`

## Verification

Source and focused-test coverage have been updated in this branch.

The local server build that originally exposed the compiler failures should be re-run after merge:

`./k8s.sh restart`

A successful Maven package/build plus normal pod startup is the required execution confirmation.
