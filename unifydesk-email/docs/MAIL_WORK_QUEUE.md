# Mail work queue

Updated October 21, 2026. Local implementation and QA are complete for this
increment. The detailed evidence, operating contract and remaining release
work are in [Mail local implementation and QA](MAIL_LOCAL_IMPLEMENTATION_AND_QA.md).

| Item | Status |
| --- | --- |
| Local builds and unit tests | Passed: 113 Linux tests, zero failures/errors/skips; both Node 24 frontend flag variants built. |
| PostgreSQL migration gate | Passed: fresh V1-V26 and upgrades from every historical version V1-V25. |
| Linux storage and inbound integration | Passed: directory fsync, real ClamAV/Rspamd, HTTP authentication/idempotency, Postfix RCPT/relay controls and SMTP-to-mailbox promotion. |
| Core reliability | Implemented/tested: bounded inbound attachment downloads, archive/trash/restore, explicit grants, current-session checks during downloads, Sent archive quota accounting and crash-safe opt-in retention. |
| Backup and recovery | Local encrypted snapshot restored to a separate database/storage location with every referenced MIME size/hash verified. Production off-node recovery remains a separate gate. |
| IAM | Implemented/tested: independent Email accounts, authenticator MFA/recovery, password recovery/change, session revocation, scoped custom organization roles, mailbox read/send/delegation and content-free audits. SSO remains optional. |
| Monitoring | Private aggregate gauges and example alert rules added; production alert installation remains pending. |

The local stack is reproducible with:

```sh
docker compose -f tests/mail-qa/compose.yaml up --build --abort-on-container-exit --exit-code-from runner
```

No production deployment, DNS/MX change, public sending or SES replacement
was performed. Optional SSO configuration, outgoing attachment uploads,
HTML rendering, IMAP and the independent production/security acceptance
requirements remain explicitly listed in the detailed report. Do not
interpret local completion as general availability.
