# SEC/QA Chat Prompt — Platform Hardening Round 1

**DO NOT START YET.**

L1 has not frozen the integrated production-code SHA.

This file is intentionally reserved now so the handoff path is stable:

`unifydesk-app/docs/platform/hardening-r1/SECURITY_QA_R1_CHAT_PROMPT.md`

Before SEC/QA starts, L1 must replace this blocked placeholder with an exact prompt containing:

- exact integrated production-code SHA;
- worker PR/final SHA lineage;
- current migration head;
- canonical check workflow/job names;
- exact CI run IDs demonstrating real steps;
- final expected Flyway default behavior;
- required clean and representative migration cases;
- exact remaining blocked external/account items.

SEC/QA must then independently verify:

1. GitHub Actions jobs received runners and executed steps;
2. required gates have no `continue-on-error` bypass;
3. backend Spotless/compile/test/clean-test/package;
4. frontend npm ci/test/build/lint/format;
5. email-provider format/test/package;
6. website deterministic install/build;
7. default Flyway baseline-on-migrate=false;
8. explicit baseline opt-in remains deliberate;
9. unmanaged/non-empty DB fails closed by default;
10. clean V1 -> current head migration;
11. representative V79 -> current head upgrade;
12. Hibernate schema validation after migration;
13. no secrets/sensitive data were introduced in workflow logs/configuration.

Evidence terms:

- SOURCE REVIEWED
- UNIT TEST EXECUTED
- INTEGRATION TEST EXECUTED
- MIGRATION TEST EXECUTED
- EXECUTION BLOCKED

No zero-step job and no `continue-on-error` green result may be called PASS.
