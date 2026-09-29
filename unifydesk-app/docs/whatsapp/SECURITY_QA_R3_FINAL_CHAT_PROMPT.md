# QA/SEC Prompt — Meta WhatsApp Round 3 Final

STATUS: NOT ACTIVE YET

Future branch:
`review/whatsapp-security-qa-r3`

Exact integrated Round-3 base SHA:
`TBD_BY_L1_AFTER_R3_INTEGRATION`

Do not start until L1 activates this prompt.

Final QA mission:
1. retest WA-R1-003 MEDIA permit timing;
2. verify TEXT/TEMPLATE permit ordering was not regressed;
3. verify post-upload limiter rejection/failure produces durable FAILED and zero message submission;
4. verify duplicate clientRequestId after failure causes no second upload/send;
5. run Redis multi-instance limiter/cooldown integration;
6. run backend full executable suite;
7. run frontend full executable suite as final regression;
8. run Flyway V1 -> V79 and upgrade validation;
9. rerun the prior targeted WA-R1 regressions;
10. complete real Meta provider verification where credentials permit.

Output:
`SECURITY_QA_FEEDBACK_R3_FINAL.md`

Final disposition must be:
- RELEASE-READY
or
- NOT RELEASE-READY

Do not fix implementation code in the QA branch.
