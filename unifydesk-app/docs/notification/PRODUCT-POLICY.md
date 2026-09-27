# Notification Product Policy

Status: frozen Round-1 policy  
Goal: useful signal, minimal interruption, no “every event -> every channel”.

## 1. Stable taxonomy

The notification registry uses stable machine keys. Titles/bodies are server-rendered from registry-owned templates; clients never supply arbitrary rendered notification content.

| Type / category | In-app | Browser push | Email default | User configurable? | Notes |
|---|---|---|---|---|---|
| `SECURITY_ACCOUNT_CRITICAL` | yes | optional high urgency | immediate mandatory | email no; push yes | password/2FA/recovery/security changes; future producers |
| `BILLING_PAYMENT_FAILED` | yes | yes | immediate | push yes, email limited | billing owner/admin target |
| `BILLING_SUBSCRIPTION_CHANGED` | yes | optional | immediate | yes except legally required notices | no repeated reminders without state change |
| `COMPANY_CREATED` | yes | no | immediate welcome | no for initial welcome | Round-1 producer; creator only |
| `COMPANY_INVITATION` | yes | optional | immediate | email generally yes | future producer |
| `MEMBER_OR_ROLE_CHANGED` | yes | optional | off | yes | avoid admin-noise email |
| `APPROVAL_REQUIRED` | yes | yes | delayed escalation after 30m unseen | yes | future producer |
| `APPROVAL_DECIDED` | yes | optional | off | yes | future producer |
| `BROADCAST_COMPLETED` | yes | optional | off | yes | future producer |
| `BROADCAST_FAILED` | yes | yes | delayed after 15m unseen | yes | future producer |
| `INBOX_ACTIVITY` | yes, coalesced | opt-in/coalesced | digest only, opt-in | yes | Round-1 producer; never one email/message |
| `INTEGRATION_DEGRADED` | yes | yes | delayed after 30m if unresolved/unseen | yes | owner/admin/permission target |
| `INTEGRATION_DISCONNECTED` | yes | yes | delayed after 15m | yes | future producer |
| `BACKGROUND_JOB_COMPLETED` | yes | optional | off | yes | user-initiated job target |
| `BACKGROUND_JOB_FAILED` | yes | yes | delayed after 15m unseen | yes | future producer |
| `QUOTA_THRESHOLD` | yes | optional | only important thresholds | yes | dedupe by threshold/window |
| `ONBOARDING_GUIDANCE` | yes | no | off | yes | low priority |
| `LOW_PRIORITY_INFO` | yes | no | off | yes | never escalation email |

Only `COMPANY_CREATED` and `INBOX_ACTIVITY` are wired to source modules in Round 1. The rest freeze policy semantics for future producers.

## 2. Severity

- `CRITICAL`: security/account integrity, payment/service-blocking conditions.
- `HIGH`: action required soon; failures/disconnections.
- `NORMAL`: useful lifecycle/activity.
- `LOW`: guidance/informational.

Severity is not permission to bypass preferences by itself. Only registry entries marked `mandatoryEmail=true` may bypass email preference/quiet-hours rules.

## 3. In-app policy

In-app is the default durable channel for product activity.

- Creating/fetching a notification never implies reading it.
- Opening the bell marks rendered items `seen`, not `read`.
- Clicking a notification marks it read before navigating.
- “Mark all read” is explicit.
- A user may mark an item unread again.
- List order: `lastEventAt DESC, notificationId DESC`.
- Header and sidebar show the same authoritative unread total for the selected company + account scope.
- Display count is capped visually at `99+`; the API returns the real bounded integer.
- The bell popover shows the newest 8 items; “View all” opens `/notifications`.
- On mobile, the bell may navigate directly to the full page instead of forcing a tiny popover.
- No sensitive inbox subject/body is stored in the notification. Inbox copy is aggregate/generic.
- Deep links are internal relative routes.

## 4. Company/account scope

Two scopes exist:

- `ACCOUNT`: user-level events independent of a workspace, primarily security/account.
- `COMPANY`: a specific company/workspace.

In a selected workspace, the bell/page returns `ACCOUNT + selected COMPANY`. A platform-admin shell without a selected workspace shows account scope only.

Company notifications are visible only while the user still has active membership and any `requiredPermission` recorded on the notification. Permission revocation must suppress future email/push delivery and hide the company notification from normal API reads.

## 5. Welcome email

`COMPANY_CREATED`:

- event key: `company.created:<companyId>`;
- target: creator/owner user;
- recipient address resolved from the current verified `users.email` at send time;
- never send to `company.business_email` merely because it is present;
- one durable in-app row;
- one logical email delivery intent;
- immediate, not subject to quiet hours;
- no browser push;
- deterministic template and safe escaped workspace name;
- provider retry follows DELIVERY-POLICY;
- duplicate source transactions/events cannot create a second logical intent.

## 6. Inbox activity policy

Source event key:

`inbox.message.received:<localMessageId>`

Eligibility:

- inbound message;
- currently unread at event-record time;
- active company member;
- current permission to read that channel (Round 1 email inbox => `email.read`).

In-app coalescing:

- group key: `inbox:<connectionId>`;
- five-minute UTC bucket;
- one `user_notifications` row per recipient/group/bucket;
- subsequent events increment `aggregateCount` and update `lastEventAt`;
- content stays generic, e.g. “3 new inbox messages”.

Browser push:

- only if the user deliberately enabled browser notifications and has an active subscription;
- coalesce by the same group key;
- do not show a system notification while any controlled UnifyDesk client is focused; post a client message instead and let realtime/in-app UI update;
- normal urgency, short TTL for routine activity.

Email:

- NEVER immediate per message;
- default `OFF` for `INBOX_ACTIVITY` to minimize surprise mail;
- user can opt into `DIGEST`;
- smart digest becomes eligible after 10 unseen/unread contributions OR 2 hours since the first contribution, whichever happens first;
- debounce for 15 minutes after the latest contribution, bounded by the 2-hour maximum wait;
- minimum 4-hour cooldown between inbox-activity digests per user/company;
- maximum 3 optional notification emails per user in a rolling 24-hour period by default;
- at send time, if all contributing notification activity is seen/read or the authoritative relevant unread inbox count is zero, suppress the email;
- digest includes counts + workspace name + a deep link, not message bodies/snippets.

## 7. Quiet hours and timezone

Defaults per company-user notification setting:

- timezone: explicit user setting, else company IANA timezone, else UTC;
- quiet hours: enabled by default `22:00-08:00` in effective timezone for optional browser push and optional email;
- in-app is never delayed by quiet hours;
- mandatory security/account email ignores quiet hours;
- a delayed/digest job inside quiet hours is moved to the next quiet-hours end;
- DST is evaluated with `ZoneId`, never by fixed UTC offset arithmetic;
- if quiet start == end, quiet hours are disabled.

## 8. Preferences and precedence

Precedence, highest first:

1. mandatory registry rule;
2. user/account/company still eligible and authorized;
3. channel is technically available;
4. per-type user override;
5. registry default;
6. quiet-hours deferral;
7. cooldown/rate limit;
8. “seen/read/resolved” suppression for delayed/digest paths.

A user may not disable durable in-app creation for security/account critical events. For ordinary product types, Round 1 still keeps in-app on because it is the application's activity history; users configure interruption channels (browser/email), not whether history exists.

## 9. Anti-fatigue rules

- no optional email for `LOW_PRIORITY_INFO`, `ONBOARDING_GUIDANCE`, routine job success or routine member changes by default;
- use delayed email only when the condition still matters;
- suppress delayed email if the corresponding in-app item was seen/read first;
- group inbox/browser activity;
- one logical delivery intent per event/user/channel/mode;
- hard caps protect against source bugs;
- a product bug that floods events must degrade toward in-app history, not an email storm.

## 10. Browser permission UX

- never request permission on login/page load;
- show an explanation before the browser prompt;
- user clicks “Enable browser notifications”;
- if permission is `denied`, do not repeatedly prompt; show browser-settings guidance;
- if unsupported, show `Unsupported on this browser/context`;
- iOS/iPadOS guidance may explain Home Screen requirement when feature detection indicates push is unavailable;
- disabling in UI revokes the server subscription and calls browser `unsubscribe()` where possible.

## 11. Email footer/preferences

Optional product email/digest includes:

- why the user is receiving it;
- same-origin “Manage notification preferences” link;
- no marketing content mixed into mandatory security/account mail.

If an optional stream is later operated as subscribed/bulk email under mailbox-provider rules, RFC-8058 one-click unsubscribe becomes mandatory for that stream before scale-up.
