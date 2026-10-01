# UX Flow

## R1 customer journey

### 1. Choose workspace
The active workspace is always visible. Switching workspace clears/invalidates every in-flight journey request through a tenant epoch.

### 2. Build audience in Contacts
Customer can:
- create Contacts manually;
- import CSV;
- organize Contacts into Lists;
- create Segments.

Broadcast should not ask them to upload/manage the same people again.

### 3. Start Broadcast
From Broadcast:
- choose channel;
- choose Contact List, Segment, or bounded individual Contacts;
- show source name and count.

Useful entry points may also exist from a Contact List/Segment page: “Create Broadcast”.

Legacy Broadcast Groups may remain for existing data/history but should not be the primary creation workflow.

### 4. Resolve recipients
Before composition/send confirmation, show:
- selected;
- eligible;
- excluded;
- exclusion reason counts;
- sample excluded recipients with safe actionable reasons.

Examples:
- 2,500 selected
- 2,312 eligible
- 82 opted out
- 46 suppressed
- 37 missing email
- 23 invalid/unavailable identity

Do not expose provider internals as eligibility reasons.

### 5. Choose channel/sender
Show:
- Email / SMS / WhatsApp availability;
- configured vs action required;
- sender label;
- safe next action.

Managed Email label is “UnifyDesk Managed Email”, not “AWS SES”.

Managed SMS should explain DLT/Header/template actions in product language.

WhatsApp should show connection/template readiness and link to the existing setup where required.

### 6. Compose
Email: subject + body/template + personalization.
SMS: approved template/header rules where required.
WhatsApp Broadcast: approved template + language + variables.

Do not force all channels into one lowest-common-denominator editor.

### 7. Preview/test
Preview renders representative personalization.

Email sender test remains separate from campaign stats. If R1 adds a Broadcast test action, it must not create production recipient/run counts.

### 8. Send now / schedule
Confirmation shows the frozen audience summary and sender.

For schedule, explicitly say the selected audience is frozen now, while consent/suppression will still be checked again before delivery.

Timezone must be visible and converted consistently.

### 9. Progress
Show:
- preparing audience;
- queued/sending;
- completed/partially failed/failed/cancelled;
- aggregate counts;
- recipient details on demand.

“Sent” means provider accepted unless final delivery evidence exists. Show Delivered/Read only for providers that report it.

### 10. Reply / Inbox
When a recipient replies:
- Inbox shows the conversation;
- if identity matches canonical Contact, show Contact name/link;
- Contact detail can open related conversation(s).

Unknown senders remain valid Inbox participants with “Create/Add to Contacts” as an explicit user action, not silent duplicate creation.

## Navigation changes

Primary sidebar relationship:
`Contacts -> Broadcast -> Inbox`

Cross-links:
- List/Segment -> Create Broadcast
- Broadcast recipient/contact -> Contact
- Contact -> related Inbox conversation
- Inbox matched participant -> Contact

## Empty/action states

No Contacts:
“Import or add Contacts first.”

No sender:
“Set up an Email/SMS/WhatsApp sender.”

Sender action required:
show product-level next action.

No eligible recipients:
do not allow send; explain exclusion summary.

Provider temporarily unavailable:
keep draft/frozen audience and allow retry after readiness recovers.

## Mobile

Audience selector, exclusion summary, compose, send confirmation and progress must remain usable without horizontal desktop-only panes. Reuse current responsive patterns from Contacts/Inbox rather than creating a second navigation system.
