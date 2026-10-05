# UnifyDesk UI/UX Revamp R1

## Purpose

UI/UX Revamp R1 establishes one interaction contract across UnifyDesk. Product and provider behavior stays authoritative; this round standardizes how the frontend communicates loading, progress, success, failure, destructive actions, and operational status.

## Loading contract

- Initial page/data load: render a skeleton that approximates the final layout.
- Background refresh: preserve existing content and show a localized refresh indicator.
- Button mutation: keep the page stable, disable the action, and show a spinner plus an action-specific label.
- Long-running work: show explicit progress/state instead of an indefinite global spinner.
- Pagination/load-more: keep existing rows visible and show progress only in the pagination control.
- Do not replace already-rendered data with a full skeleton during refresh.

Reusable primitives live under `frontend/src/components/ui`.

## Mutation and upload contract

- Mutation buttons are single-flight while their request is active.
- A form cannot submit while a required/selected upload is unresolved.
- Upload failure must leave a clear retry/remove path.
- Workspace creation is disabled while its selected logo is uploading.
- Registration is disabled while its selected profile picture is uploading.

## Confirmation contract

Do not use browser-native `window.confirm()`.

Use `ConfirmDialog` for logout, delete, revoke, suspend, retire, disconnect, provider mutations, and other consequential actions.

Buttons must describe the decision explicitly, for example:

- No, stay / Yes, log out
- No, keep account / Yes, delete account
- No, cancel / Yes, suspend
- No, keep sender / Yes, disable sender

Destructive confirmations use danger styling. Reversible but consequential actions use warning styling.

## Operational status color contract

Brand indigo is not an operational status color.

- Green: completed, active, ready, delivered, accepted, verified.
- Blue: running, processing, sending, syncing, retrying.
- Light blue: queued, scheduled, received, pending.
- Amber: action required, warning, paused, throttled, expired.
- Orange: partial failure.
- Red: failed, rejected, bounced, complaint, policy suspension.
- Violet: clicked/engagement accent where applicable.
- Grey: cancelled, skipped, suppressed, disabled, retired, deleted, unknown, draft.

Use `StatusBadge` rather than defining status color maps in feature pages.

## Feedback contract

- Snackbar: transient result of a user action.
- Inline alert: information that must remain visible in page context.
- Error state: failed initial content load; include Retry when the read is recoverable.
- Empty state: successful load with no records; explain the next useful action.
- Field error: validation tied to one field.

## Dialog/accessibility contract

Dialogs must:

- expose dialog semantics and accessible title/description;
- move focus into the dialog;
- support Escape when no mutation is in progress;
- prevent dismissal while a destructive mutation is running;
- return a clear Cancel/No path;
- remain usable on mobile as a constrained modal/sheet.

## Revamp coverage

R1 foundation and migration covers the highest-use surfaces:

- auth and registration;
- workspace create/select/switch;
- logout and account deletion;
- company users, invitations and roles;
- contacts, lists, segments and imports loading surfaces;
- Inbox and WhatsApp conversation/message loading;
- Broadcast history, recipient delivery status and provider setup;
- Managed Email and Managed SMS setup/status;
- notifications and templates;
- billing, plan selection and platform administration;
- webhook callback operational status;
- Ads Center pages and Google/Reachlyo operational sub-panels.

Localized option loading and load-more labels remain localized by design.

## Review checklist

Before accepting new UI:

1. No native browser confirmation.
2. No blank page with only “Loading…”.
3. No duplicate submission while a mutation/upload is active.
4. Existing data remains visible during refresh.
5. Operational statuses use semantic colors.
6. Error and empty states are distinct.
7. Destructive actions require an explicit confirmation.
8. Dialog and icon-only actions are keyboard/accessibility safe.
9. Mobile layout remains usable.
10. Provider/internal terminology is not exposed unnecessarily to customer-facing surfaces.
