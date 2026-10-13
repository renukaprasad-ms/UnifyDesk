# UnifyDesk CRM — Product and Navigation Blueprint

**Status:** Approved product direction; documentation only, not yet implemented  
**Date:** 2026-10-10  
**Repository area:** unifydesk-app  
**Target branch:** dev

**Implementation update (2026-10-13):** The default Deals pipeline, unified Activities screen, internal notes, and basic Reports are now implemented. See [CRM Sales Workspace](CRM-SALES-WORKSPACE.md) for the current scope, permissions, migrations, and verification. The phases below remain the original product roadmap.

## 1. Product decision

We will introduce a top-level **CRM** module in UnifyDesk instead of keeping **Contacts** as an isolated primary-navigation destination. The label will be **CRM**: it is short, recognizable to business users, and can accommodate Leads, Contacts, Companies, Deals, Tasks, Activities, and Reports.

Clicking **CRM** in the primary sidebar navigates to the CRM Overview and displays a persistent **secondary/contextual sidebar** alongside the existing primary navigation, following the layout pattern already used by Ad Center, Inbox and Broadcast.

This is an extension of the existing Contacts & Audience Management functionality, **not** a rewrite and **not** a separate new app. Preserve the current Contacts, Inbox, Broadcast, consent, segments, and associated behavior.

## 2. Navigation and information architecture

### Primary app sidebar (proposed)

- Dashboard (existing application-wide overview)
- Notifications
- **CRM** (replaces standalone top-level Contacts item once CRM routes are ready)
- Broadcast
- Inbox
- Ad Center
- Templates
- Company
- Administration (authorized users only)
- Account

The primary CRM link should remain active on every CRM route. Selecting it should open the CRM contextual sidebar and navigate to /crm/overview; secondary navigation remains visible while visiting CRM subpages.

### CRM contextual/secondary sidebar

| Order | Item | Destination | Purpose | Release |
| --- | --- | --- | --- | --- |
| 1 | Overview | /crm/overview | CRM KPIs, assigned work and recent customer activity | R1 |
| 2 | Leads | /crm/leads | Prospect tracking, qualification and conversion | R1 |
| 3 | Contacts | /crm/contacts | Unified existing contact directory and Customer 360 profiles | R1 |
| 4 | Companies | /crm/companies | B2B customer/account records and their contacts | R1 |
| 5 | Deals | /crm/deals | Opportunities, sales pipelines and Kanban board | R2 |
| 6 | Tasks | /crm/tasks | Follow-ups, reminders and assignees | R1 |
| 7 | Activities | /crm/activities | Customer relationship timeline and event filtering | R1 basic, R3 extended |
| 8 | Reports | /crm/reports | Lead/deal conversion and team reporting | R2 basic, R3 extended |

Within **Contacts**, maintain accessible navigation for the existing features:

- All Contacts: /crm/contacts
- Lists: /crm/contacts/lists
- Segments: /crm/contacts/segments
- Imports: /crm/contacts/imports
- Settings: /crm/contacts/settings

A collapsible subsection within the CRM secondary sidebar is preferable to a third persistent rail. Existing Contact Details, list details, segment editor and import details remain reachable without losing CRM navigation context.

**Release gating:** Do not display dead links or imply unfinished modules are operational. Use feature flags/plan entitlements/permissions to expose only implemented sections. When migrating, maintain existing /contacts routes as redirects or compatible aliases to the corresponding /crm/contacts routes, preserving detail IDs and deep links. Check route matching order so /crm/contacts/imports does not collide with /crm/contacts/:contactId.

### UX requirements

1. Desktop: primary sidebar + one CRM contextual sidebar + flexible, full-width content. Avoid accidental third sidebars, excessive padding and nested scrolling.
2. Mobile/tablet: CRM secondary navigation opens via a clear toggle/drawer, closes on selection, Escape or outside click and manages focus accessibly.
3. Refresh and direct deep linking preserve the selected CRM route, active navigation item and current workspace. Selection is route-derived, not only transient React state.
4. Light/dark themes, selected item styling, thin theme-aware scrollbars, loading skeletons, empty-state CTAs and confirmation dialogs use existing design components.
5. On workspace switch, invalidate stale async requests via existing workspace epoch patterns; close stale drawers, clear old tenant data, and enforce company authorization.
6. Treat the existing /workspace Dashboard as platform-wide; **CRM Overview** is a distinct sales/relationship dashboard.
7. Proper role-based visibility is required for each item, with equivalent backend authorization and plan entitlements. Hiding links is not security.

### Proposed UI integration points

The current dev implementation uses:

- frontend/src/components/navigation/AppSidebar.tsx — standalone Contacts primary link today.
- frontend/src/layouts/MainLayout.tsx — Inbox/Broadcast/Ad Center secondary-sidebar decisions.
- frontend/src/components/navigation/AdCenterSidebar.tsx — visual/reference pattern.
- frontend/src/components/contacts/ContactsNav.tsx — existing Contacts tabs.
- frontend/src/routes/AppRoutes.tsx — guarded /contacts and nested routes.
- frontend/src/features/iam/permissions.ts — existing Contact permissions.
- frontend/src/pages/contacts/* and frontend/src/features/contacts/* — functionality to preserve.

The implementation should add a dedicated CRM contextual navigation component, integrate CRM route detection in MainLayout, rehome the Contacts entry in AppSidebar, and reuse existing Contacts screens under the new route hierarchy rather than copying pages.

## 3. Why UnifyDesk can support a CRM

The existing Contacts module provides person records, multiple normalized identities, tags, lists, dynamic segments, typed custom fields, consent/preferences/suppressions, merging, CSV import/export, owner fields and a basic activity feed. Existing Inbox and Broadcast contact activity read adapters give us a valuable starting point for Customer 360.

What it does **not** yet provide as a first-class CRM: lead lifecycle and qualification, independent customer account records, actionable follow-up tasks, sales pipelines/deals, forecasting, and full CRM reporting.

Contacts & Audience architecture deliberately deferred these. This roadmap extends those boundaries, rather than treating its existing Contact model as a complete CRM.

## 4. Feature specification and delivery phases

### R1 — CRM Lite and navigation

**CRM Overview**
- Total contacts, new and active leads, tasks due/overdue, leads requiring follow-up, recent activity.
- Date range and current-company scope must be explicit; safe empty states when little data exists.

**Leads**
- No second duplicate people table: represent a lead as an existing Contact with distinct CRM lifecycle state.
- Suggested states: NEW, CONTACTED, QUALIFIED, UNQUALIFIED, CONVERTED. Persist state transitions and actor/timestamp.
- Source, assignee/owner, priority, notes, lead status and next follow-up.
- Conversion preserves the underlying Contact record and consent; linking an Account or later Deal must be explicit.
- Lead status and sales communication must never auto-set marketing opt-in.

**Contacts and Customer 360**
- Retain Contact IDs, identities, merge rules, tags, custom fields, lists, segments, imports/exports, channel preferences, suppressions, Broadcast audience integration and existing Contact permissions.
- Upgrade the Contact Detail UX with CRM owner, lead status, linked customer company, tasks, dated notes and activity timeline.
- Make Inbox and Broadcast interaction details accessible via provider-neutral references without copying email/message bodies to Contacts.

**Companies (CRM Accounts)**
- A CRM Account represents a customer's business or organization, NOT the UnifyDesk tenant/company.
- Provide account name, optional domain/industry, owner and relationship to one or more Contacts.
- The existing Contact.organization free-text field is not itself a CRM Account FK. Avoid auto-creating false accounts from unverified strings.

**Tasks**
- Follow-up calls, emails, meetings and to-dos, including assignee, due date/timezone, status, priority and optional links to Contact/Lead/Account.
- Views: Mine, Team (permission dependent), Due today, Overdue, Upcoming, Completed.
- Add tasks from Lead and Contact detail.
- Reuse existing Notifications capability for reminders; do not create a second scheduler or notification stack.

**Activities**
- Cross-module paginated timeline assembled from CRM-owned activity and the existing Inbox and Broadcast read adapters.
- Events link back to source systems; avoid copying provider payloads and message contents into a new central table.
- Separate internal notes from customer-facing communication events.

### R2 — Sales CRM

**Deals**
- Default sales pipeline with ordered stages; configurable stage management can follow.
- List/Kanban views, stage movement, assigned owner, amount and currency, expected close date, Contact and Account associations.
- Example default stages: NEW, QUALIFIED, PROPOSAL, NEGOTIATION, WON, LOST; rename according to product research before freezing contracts.
- Stage transition audit, validation and optimistic-concurrency protection.
- Pipeline money is opportunity value, not proof of payments or a Billing record.

**Reports**
- Basic lead source/stage funnel, open/won/lost deal counts, pipeline value, overdue follow-ups and owner/team activity.

### R3 — CRM connected automation and advanced analytics

- Cross-channel workflows triggered by permitted CRM events with explicit consent and provider-policy checks.
- Richer customer insights, source attribution, conversion cohorts, reminders and sales forecasts.
- Optional lead capture from forms, campaigns and supported integrations.
- Introduce more advanced segmentation or event-based filters only behind bounded, indexed query contracts.
- No speculative AI layer is needed in R1.

## 5. Architecture and data boundaries

Stay within the existing modular Spring Boot application + PostgreSQL. Add a focused CRM module (for example com.messaging.crm with api/application/domain/repository/integration boundaries) that interacts with Contacts and Inbox/Broadcast through narrow interfaces. No new microservice, Kafka cluster, Redis cluster, OpenSearch service, or duplicate customer database is justified for R1.

Potential tables (names provisional; Lead architect must freeze migrations and constraints):

- crm_contact_lifecycle / crm_lead_stage_events — company-scoped lifecycle projections and immutable stage history referencing canonical Contact IDs.
- crm_accounts / crm_account_contact_links — customer company identities and associations.
- crm_tasks — assignees, deadlines, statuses and related CRM records.
- crm_notes — dated, authored notes and visibility.
- crm_activity_events — CRM-specific immutable events only, not duplicate messages.
- R2: crm_pipelines / crm_pipeline_stages / crm_deals / crm_deal_contacts / crm_deal_stage_events.

Non-negotiable engineering rules:

1. Every mutable CRM table carries company_id; every request verifies tenant and record ownership.
2. Preserve canonical Contact IDs; leads are a state/view of Contacts, not a separate identity store.
3. Index company_id + frequently filtered state/assignee/due date/timestamp columns. Prefer keyset pagination and bounded joins; avoid N+1 queries.
4. Reuse existing IAM, plan entitlements, workspace epoch isolation, Flyway, notification services, AsyncJob, metrics/logging and storage accounting.
5. Add permission keys for crm.lead.*, crm.account.*, crm.deal.*, crm.task.*, crm.activity.read and crm.report.read as features become real. Preserve contact.* and contact.consent.* unchanged; never replace granular access with blanket CRM permission.
6. Exact currency representation and validation are required for Deals; no floating-point money.
7. Audit changes; design merge/deletion and retention semantics, including tasks/deals referencing merged Contacts.
8. All data changes should support safe retry behavior, optimistic locking, and correct company/user authorization.
9. Report queries must remain bounded; heavy exports/aggregates use existing asynchronous infrastructure.
10. Consent/suppression and provider eligibility remain independent of lead/deal/customer lifecycle state.

## 6. Release and acceptance criteria

**Navigation acceptance (R1):**
- CRM appears in the primary sidebar for permitted users; clicking navigates to /crm/overview and shows the secondary CRM sidebar.
- The old Contacts pages work under /crm/contacts without losing imports, lists, segments, settings, consent or existing data.
- Existing /contacts deep links remain functional via compatibility handling.
- A refresh and workspace switch keep the correct active route, authorized content, theme and secondary sidebar.
- On mobile the secondary drawer can be opened/closed with keyboard and screen-reader support; no dead links.

**Domain acceptance (R1):**
- Creating/qualifying a lead uses exactly one canonical Contact.
- Creating/linking CRM Accounts cannot cross tenant boundaries.
- Tasks due/overdue and completion flows work with timezone-safe timestamps and assignee permissions.
- Customer 360 displays accurate Contact/Inbox/Broadcast history without duplicating sensitive message bodies.
- No CRM change silently creates marketing opt-in or bypasses existing suppression rules.

**Delivery order:** Lead architecture/contracts/migration plan -> B1 CRM domains -> B2 Inbox/Broadcast/notification adapters -> F1 navigation/profile UX -> Lead integration -> independent security/QA (tenant isolation, permissions, concurrency, UX and migrations). Ship CRM navigation and CRM Lite as a coherent unit; R2/R3 are later scoped releases.

## 7. Non-goals for this document

- Do not implement, rename or migrate code solely because this document exists.
- Do not replace or weaken the Contacts & Audience, Inbox, Broadcast or Billing contracts.
- Do not promise HubSpot/Zoho feature parity in the first release.
- Do not add costly new infrastructure for a basic CRM.
- Do not equate UnifyDesk tenant companies with a customer's CRM Account.
- Do not expose unimplemented features as working UI.

## 8. References in this repository

- docs/contacts-audience/README.md
- docs/contacts-audience/ARCHITECTURE.md
- docs/contacts-audience/DATA-MODEL.md
- docs/contacts-audience/SEGMENTATION.md
- docs/contacts-audience/INTEGRATIONS.md
- frontend/src/components/navigation/AppSidebar.tsx
- frontend/src/layouts/MainLayout.tsx
- frontend/src/components/navigation/AdCenterSidebar.tsx
- frontend/src/routes/AppRoutes.tsx

**Decision recap:** Call it **CRM**; clicking the primary-sidebar item opens a CRM contextual/secondary sidebar with Overview, Leads, Contacts, Companies, Deals, Tasks, Activities and Reports. Preserve existing Contacts capabilities and build in phased, production-grade increments.
