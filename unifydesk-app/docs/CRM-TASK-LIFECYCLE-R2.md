# CRM Task Lifecycle R2 — Reminder and Assignee Safety

Branch: `feature/crm-task-lifecycle-r2`, created from `dev` at `c0f33c9abf5700626908d873950f3046edc1fb60`.

## Implemented source

- Flyway `V110__crm_task_reminders_r2.sql` adds `reminder_generation`, `reminded_generation`, and a partial due-date scan index on active tasks.
- `CrmTaskReminderScheduler`: bounded (50 tasks/run), PostgreSQL `FOR UPDATE SKIP LOCKED` task claims, idempotent notification event recording in the same transaction. Default schedule is once per minute, and only scans tasks due within 10 minutes or overdue by no more than 24 hours.
- `NotificationEventRecorder.recordCrmTaskDue`, `NotificationTypeRegistry`, `NotificationMaterializer`: a durable **in-app notification only**, rendered as "CRM follow-up due"; default email OFF, no new provider costs. Recipient must be an active member with both `crm.task.read` and `contact.read`. On fanout, the task still must be active, assigned to that user, and match the reminder generation; stale reminders are suppressed.
- Reminders are rearmed when due date or assignee changes and after reopening a completed/cancelled task. Editing other task details does not intentionally rearm reminders.
- `CrmTaskMembershipLifecycle` joins the existing CompanyService transaction when a user is suspended, removed or leaves. **OPEN/IN_PROGRESS** tasks are reassigned to an active workspace OWNER. Completed and cancelled task history remains attributed to the former assignee. Customer-company ownership is cleared.
- User deletion/purge lifecycle also rehomes unfinished tasks before deleting membership rows. Missing active owner yields HTTP 409 rather than silently abandoning open work. A workspace owner must transfer ownership before account purging.
- Existing assignee membership is locked when creating or editing tasks to guard against racing member-status updates.

## Configuration

**Default is disabled** until database migrations and live notification delivery are verified.

```env
APP_CRM_TASK_REMINDERS_ENABLED=false
APP_CRM_TASK_REMINDERS_POLL_INTERVAL_MS=60000
APP_CRM_TASK_REMINDERS_INITIAL_DELAY_MS=30000
```

Once the Flyway migration and notification outbox/realtime flow pass in a target environment, set `APP_CRM_TASK_REMINDERS_ENABLED=true` for the backend. Deploys using multiple k3s replicas are safe due to PostgreSQL row-level `SKIP LOCKED` claims. This feature does not send provider email, SMS or WhatsApp.

## QA and release gates

Added `CrmTaskLifecycleR2PostgresTests` for reassignment, failure when owner unavailable, reminder idempotency, due date/version rearming, and user purge. Added `CrmReminderNotificationRegistryTests` for rendering and default email behavior.

**Not yet executed**: Java 25 compile, Maven/Spotless, V110 Flyway PostgreSQL upgrade, real notification materialization, multi-replica concurrency and end-to-end browser QA. This environment has GitHub connector access but no authenticated full repository checkout or matching Java 25/PostgreSQL test stack. Static source checks are not a substitute for those gates.

## Out of scope

- Email/SMS/WhatsApp task reminders are not added (avoids new provider costs).
- Notification preferences may be expanded later; R2 currently uses existing in-app notification behavior.
- Deals and visual sales pipelines remain separate CRM R2+ product work.
