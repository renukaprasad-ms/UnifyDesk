# Managed Email Security & QA Feedback

**Owner during review:** Security & QA  
**Current status:** WAITING_FOR_ROUND_1_INTEGRATION

Do not record speculative architecture here before an integrated implementation exists.

For every finding use:

## ME-SEC-XXX — Finding title

- **Severity:** critical | high | medium | low | informational
- **Area:** authz | tenant isolation | AWS/SES | DNS | sender spoofing | abuse | events | suppression | billing | privacy | reliability | frontend | other
- **Affected flow/files:**
- **Preconditions:**
- **Reproduction:**
- **Expected result:**
- **Actual result:**
- **Evidence:**
- **Security/business impact:**
- **Required invariant:**
- **Suggested remediation direction:**
- **Regression test required:**
- **Lead disposition:** pending | accepted | rejected | deferred
- **Round fixed:**
- **Retest result:**

## Required test matrix

At minimum test:

- Company A cannot read/change Company B domain, SES tenant, sender, DNS records, suppression entries, usage or events.
- A user cannot put another company's sender ID/domain ID in a request and bypass ownership.
- Inactive/unverified/suspended managed senders cannot launch Broadcasts.
- A forged/replayed provider event cannot mutate state.
- Event correlation cannot cross companies.
- DNS records are returned only for the requested company's onboarding object.
- Platform subdomain allocation is globally unique and reserved names are blocked.
- Sender From address cannot escape its verified customer domain or allocated platform subdomain.
- Managed mode never exposes UnifyDesk AWS credentials.
- Send limits are enforced across multiple backend pods.
- Duplicate requests/events do not double-provision, double-send or double-bill.
- Bounces/complaints feed suppression correctly.
- Suppressed recipients are not sent again.
- Tenant/reputation suspension blocks new sends promptly.
- Broadcast `UNKNOWN` semantics still prevent unsafe blind retries.
- Delete/disconnect does not orphan active SES resources or enable stale senders.
- Permission/RBAC checks are present on every managed-email API.
- Logs and API responses do not leak secrets.
- Frontend never treats "DNS records copied" as "verified".
- Accessibility and mobile flows remain usable.
