# Contacts & Audience Team Workflow

## Ground rule

Workers implement frozen contracts. They do not redesign architecture independently.

## Round 0 — Lead

Lead:
1. inspected current dev and existing module workflows;
2. researched current Contacts/Audience products/providers;
3. froze data/API/security/performance contracts;
4. reserved migrations;
5. merged architecture package;
6. publishes exact worker base SHA.

## Round 1 workers

All branches are created from the exact same frozen SHA.

B1:
feature/contacts-core-r1

B2:
feature/contacts-audience-processing-r1

F1:
feature/contacts-audience-ui-r1

Workers:
- do not branch from moving dev;
- do not cherry-pick each other;
- do not edit frozen architecture docs;
- do not merge their own PR;
- list every existing/shared file changed.

## Integration order

Lead reviews independently first.

Preferred integration:
1. B1
2. B2
3. F1
4. Lead V82/cross-module glue
5. full compile/test
6. freeze integrated SHA

Integration order does not mean workers depend on each other.

## QA/SEC

Branch:
review/contacts-audience-security-qa-r1

Created from exact integrated SHA, not moving dev.

QA is review/test/report only. It does not silently fix implementation.

Output:
SECURITY_QA_FEEDBACK.md

Every finding includes:
- ID CA-R1-NNN
- severity
- component/owner
- invariant violated
- evidence
- reproduction
- recommended acceptance condition

## Round 2

Lead triages findings:
- accepted
- rejected with reason
- deferred with explicit risk

Only accepted actual findings generate targeted worker prompts/branches.

## Shared-file rule

If a worker discovers an unassigned shared-file change, it records an integration request in its PR. It does not grab ownership.

Frozen docs are Lead-owned:
- ARCHITECTURE.md
- DATA-MODEL.md
- API-CONTRACT.md
- DECISIONS.md
- TASKS.md
- INTEGRATION_STATUS.md

QA report is QA-owned during a review round.
