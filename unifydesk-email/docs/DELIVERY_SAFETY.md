# Delivery safety and release boundaries

## Unknown SMTP submission outcomes

The service differentiates local Postfix acceptance (HANDED_OFF), pre-DATA
temporary failures (DEFERRED), known permanent failures (FAILED), and uncertain
outcomes after DATA may have reached Postfix (UNKNOWN).

UNKNOWN is a terminal *automatic retry* state, not proof of delivery or failure.
Do not automatically resubmit. Consult Postfix queue IDs/logs and delivery feedback,
then reconcile manually or via a separately audited recovery workflow.

Stale PROCESSING claims are quarantined to UNKNOWN because the worker may have
crashed after SMTP DATA but before persisting its result. This deliberately
trades possible missed sends for duplicate-send prevention.

The message aggregate reports DEFERRED while UNKNOWN recipients remain unresolved,
and recipient details expose the precise UNKNOWN state.

## Still required before enabling customer traffic

- Add bounded per-company, per-domain and global rate/quota limits with atomic shared
  claims, cooldowns, a kill switch, and outage fail-closed behavior.
- Add transport-level TLS/mTLS and private ingress between UnifyDesk backend and
  this dedicated service.
- Implement integration of the standalone engine into the Super Admin email-provider
  selection and the normal broadcasting/financial authorization path.
- Test delivery with live Postfix, DNS and the target MX providers before release.
- Review feedback replay/deduplication and crash races under PostgreSQL concurrency.
- Separate sending IPs from validation probe infrastructure and warm up new IPs.
- Implement controlled support for IP pools, reputation containment, opt-outs,
  abuse handling and account recovery.

GitHub Actions pipeline changes are intentionally outside this branch.
