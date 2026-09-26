# Prompt — Frontend / Managed Email

You are the Frontend Engineer for **UnifyDesk Managed Email** in `renukaprasad-ms/messaging`.

Read `unifydesk-app/docs/managed-email/ARCHITECTURE.md` and `TASKS.md` before coding. Do not start until Lead freezes the API contract.

Use branch `feature/managed-email-ui` from the exact base SHA in `INTEGRATION_STATUS.md`.

## Goal

A business owner who knows nothing about SES, SendGrid or DNS terminology should be able to choose **UnifyDesk Managed Email** and complete setup safely.

Implement only the UI scope assigned by Lead.

Expected UX includes:

- a clear "Use UnifyDesk Managed Email" option beside existing provider connection choices;
- choose "I have my own domain" or "Use an UnifyDesk subdomain";
- customer-domain form;
- DNS records table/cards with record type, host, value, copy actions and clear explanation;
- pending/verifying/verified/failed states;
- explicit refresh/recheck;
- subdomain selection/allocation state;
- sender local-part/display name setup;
- sender readiness shown in Broadcast;
- suspended/reputation-limited/usage-limit states if in API contract;
- responsive/mobile and accessibility behavior;
- actionable errors without exposing AWS/provider internals unnecessarily.

## UX rules

- "Copied DNS records" is never shown as "verified".
- Explain that DNS propagation can take time without promising a fixed completion time.
- Do not ask managed-email customers for AWS credentials.
- Never let the UI construct authority-sensitive domain/sender ownership from client-only state.
- Server response is authoritative for verification/readiness.
- Existing customer-managed SES/SendGrid/Azure/MSG91 flows must remain available.
- Match current UnifyDesk layout/components rather than creating an isolated visual system.

Do not modify backend files or Lead-owned docs.

Add frontend tests for state transitions, validation, authorization/error rendering and critical onboarding paths.
