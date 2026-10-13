# CRM sales workspace

Deals, Activities, and Reports are implemented as native workspace-scoped modules. Migration V113 adds deals, deal history, and internal notes. V112 supplies task history. Rebuild the selected local/tunnel mode; Flyway applies the new migrations automatically without deleting existing data.

## Deals

- `/crm/deals` provides a default six-stage pipeline: New, Qualified, Proposal, Negotiation, Won, Lost.
- Board and list views, title search, stage filtering, and cursor pagination. Board counts describe the loaded records; use Load more for additional records.
- Create/edit deal title, description, exact amount/currency, owner, expected close date, and optional contact/customer-company associations.
- Drag cards between stages or use their keyboard-accessible stage selectors. Closed deals can be reopened. Version checks reject stale updates.
- Detail/history panel shows who created, edited, or moved a deal and when. Audit writes and deal mutations share a transaction.
- Money is stored as PostgreSQL numeric and passed as decimal strings. Currency-specific fractional precision is validated. Opportunity value is not a payment or recognized revenue.
- Composite foreign keys and service validation prevent cross-workspace associations. Contact merges transfer deal/note links; company purge drains records and cascades their histories. Deleting a linked contact or customer company clears the deal association.

## Activities

- `/crm/activities` assembles a paginated timeline from lead/task/deal history, contact events, internal notes, and read-only Inbox/Broadcast metadata.
- Filter by source, actor name/username, related contact, and date range. The default range is 30 days; maximum range is one year. Stable cursors include time, source, and ID to avoid collisions across source tables.
- Open source links resolve to the original record or module. Provider message contents are not copied into CRM tables.
- Internal notes are explicitly separate from customer communications. Notes can be workspace-wide or contact-bound. They are immutable recorded entries; adding one sends no customer message.
- Sources are filtered server-side using the viewer's source permissions. Old task changes predating V112 cannot be reconstructed. Provider entries describe current conversation/recipient activity, rather than an immutable audit of every provider event.

## Reports

- `/crm/reports` provides current lead/deal/task stage distributions, lead conversion ratio, overdue follow-ups, and pipeline values grouped by currency.
- 7/30/90-day activity periods show new leads, new deals, task completion events, and the top 20 recorded team contributors. Daily buckets use UTC. Snapshot metrics are labelled separately from period metrics.
- CSV export contains the displayed aggregates and daily buckets. Currency values remain separate; user-supplied cells are escaped against spreadsheet formula interpretation.
- No invented source attribution, forecasts, exchange-rate conversion, or backfilled task history. Customizable/multiple pipelines and advanced forecasting remain extensions to this default pipeline.

## Permissions

- `crm.deal.read`, `crm.deal.modify`
- `crm.activity.read`, `crm.activity.modify` (internal notes)
- `crm.report.read`

Owners use the catalog. Managers receive read/write defaults; members receive read defaults. Custom roles can grant these keys explicitly. Deals also require Contact and CRM Company read permissions; Reports require Contact, Lead, Deal, and Task read permissions. Activities independently omit sources the viewer cannot read.

## Verification

Backend integration tests cover decimal precision, cross-workspace references, stale versions, status/history atomicity, currency-separated reporting, feed pagination/permissions, and contact merge preservation. Frontend interaction tests cover exact value submission, conflict visibility after refresh, read-only mutation controls, and cross-source feed pagination. These use the isolated Compose test database rather than application data.
