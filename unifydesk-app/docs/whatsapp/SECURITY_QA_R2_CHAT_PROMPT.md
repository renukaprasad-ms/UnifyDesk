# QA/SEC Prompt — Meta WhatsApp Round 2

STATUS: NOT ACTIVE YET

Future branch:
`review/whatsapp-security-qa-r2`

Exact integrated Round-2 base SHA:
`TBD_BY_L1_AFTER_R2_INTEGRATION`

Do NOT start until L1 activates this prompt with one exact immutable SHA.

When activated:
1. retest every WA-R1-001 through WA-R1-008 finding;
2. run the complete executable backend/frontend/Flyway matrix;
3. execute Redis rate-limit concurrency tests;
4. execute inbound media crash/retry fault injection;
5. execute stale-PENDING restart recovery;
6. verify detailed READ/DELIVERED status after HTTP reload and realtime event;
7. verify granular-scope intersection;
8. verify template URL-button variables;
9. repeat the broader Round-1 regression/security plan;
10. clearly separate real Meta provider verification from local/mocked tests.

Do not accept weakened QA tests as remediation.

Output:
`SECURITY_QA_FEEDBACK_R2.md`

L1 will fill the exact SHA and detailed integrated inputs after B1/B2/F1 complete.
