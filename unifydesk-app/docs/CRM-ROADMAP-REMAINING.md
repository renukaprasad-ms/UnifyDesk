# UnifyDesk CRM — Remaining Features & Roadmap
### Post-Custom Fields Implementation Status · October 2026

---

## 1. Executive Summary & Progress

With the completion of **CRM Custom Fields (R1)**, UnifyDesk CRM now provides dynamic, tenant-scoped schema customization across all three core entities:
- **Leads**: Custom attributes in Create Modal, Lead History Drawer, inline editing, and list view.
- **Deals**: Custom attributes in Create Deal dialog, Edit Deal dialog, Opportunity details drawer, Kanban cards, and pipeline table view.
- **Companies (Accounts)**: Custom attributes in Create Company modal, Company Directory list, and Company Detail Profile page.

Custom fields were ranked **#1** in our competitive gap analysis against **Zoho CRM** and **Microsoft Dynamics 365**. Below is the detailed breakdown of **what remains** to take UnifyDesk from a functional CRM to a market-leading, communication-first sales platform.

---

## 2. Competitive Matrix Status

| Module / Capability | Status in UnifyDesk | Zoho CRM | Microsoft Dynamics 365 | Next Action |
|---|---|---|---|---|
| **Custom Fields (Leads, Deals, Accounts)** | ✅ **Done** | Native | Native | Fully shipped & verified |
| **WhatsApp 1-Click Action from CRM** | ✅ **Done** | Third-party / Add-on | Add-on only | **Shipped (1-Click template/free-form + CRM timeline)** |
| **Multiple Sales Pipelines & Custom Stages** | ✅ **Done** | Unlimited | Multiple Business Process Flows | **Shipped (Pipelines, custom stages, weighted probs, Kanban)** |
| **Web-to-Lead Capture Forms** | ✅ **Done** | Web Forms builder | Power Pages / Portals | **Shipped (Form Builder, Public Submissions, Embeds, Analytics)** |
| **Deal Document / File Attachments** | ✅ **Done** | Native attachments | SharePoint / OneDrive | **Shipped (Proposals, Pitch Decks, Contracts, Drag & Drop Upload)** |
| **Sales Forecasting & Weighted Pipelines** | ✅ **Done** | Native forecasting | AI-driven forecasting | **Shipped (Probabilistic Forecast, Close Cohorts, Rep Quotas & Dashboard)** |
| **Campaign & Broadcast Attribution** | ✅ **Done** | Campaigns module | Marketing Insights | **Shipped (Closed-loop attribution, UTM tracking, ROI Leaderboard & Dashboard)** |
| **Rule-Based Lead Scoring** | ❌ **Missing** | Rule scoring / Zia | Predictive Scoring | **Tier 2 (Next)** |
| **CRM Workflow Automation / Triggers** | ❌ **Manual stage changes** | Blueprints & Workflows | Power Automate | **Tier 3 (Medium)** |
| **Meeting Scheduler / Calendar Sync** | ❌ **Missing** | Zoho Bookings / Calendar | Outlook / Teams Bookings | **Tier 3 (Medium)** |
| **Telephony / Click-to-Call** | ❌ **Missing** | PhoneBridge | Teams Calling | **Tier 4 (Future / R3+)** |
| **Quotes & Invoicing (CPQ)** | ❌ **Missing** | Zoho Books / CPQ | Dynamics Finance / Commerce | **Tier 4 (Future / R3+)** |

---

## 3. Detailed Breakdown of Remaining Items

### 🚀 Tier 1: Immediate High-Impact Next Steps (Build Next)

#### 1. WhatsApp 1-Click Action from Leads & Deals (UnifyDesk's Superpower)
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - `CrmWhatsAppController` + `CrmWhatsAppService`: Context endpoint detects connected WhatsApp channel, contact phone identities, conversation state, and 24h customer window (`freeFormAllowed`).
  - Approved WhatsApp templates retrieved directly from `MetaWhatsAppTemplateService`.
  - `SendWhatsAppModal`: Live WhatsApp chat preview bubble with real-time variable substitutions (`{{1}}`, `{{2}}`), 24h session indicators, and free-form or template message dispatch.
  - One-click trigger button in `LeadHistoryPanel` (Leads) and `DealDetails` drawer (Deals).
  - Outgoing message automatically recorded in CRM notes and deal activity timelines.

#### 2. Multiple Sales Pipelines & Custom Stages
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - `crm_pipelines` & `crm_pipeline_stages` schema tables with Flyway migration `V117__crm_multiple_pipelines.sql`.
  - Auto-initialization and backfill of default "Standard Sales Pipeline" with 6 standard stages for all existing and new companies.
  - `CrmPipelineService` & `CrmPipelineController`: Full CRUD for custom sales pipelines and stages (categories OPEN/WON/LOST, win probability % 0-100, custom stage colors).
  - Dynamic Kanban Board: columns rendered dynamically based on active pipeline's configured stages, displaying win probabilities, deal count, and column currency total.
  - Top Bar Pipeline Switcher dropdown + `PipelineManagerModal` to create, edit, reorder stages, set default, and delete pipelines.
  - Create & Edit Deal dialog with interactive Pipeline and Stage selector.

#### 3. Web-to-Lead Capture Forms
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V118__crm_lead_forms.sql`**: Added `crm_lead_forms` and `crm_form_submissions` tables with counters, indexed keys, and JSONB fields.
  - **Service & Ingestion Engine (`CrmLeadFormService`)**:
    - Automatic identity resolution via normalized email / phone number matching against existing contacts.
    - Creates new contacts with `source = 'WEB_FORM'` when no match exists.
    - Automatic lead qualification & enrollment into `crm_leads` with configured default lead stage.
    - Saves custom field values via `CrmCustomFieldService`.
    - Logs form inquiry in `crm_notes` and records submission history with IP, User-Agent, and full payload snapshot.
  - **Public REST API (`CrmPublicLeadFormController`)**:
    - `GET /api/v1/public/crm/forms/{formKey}`: Retrieves active form schema.
    - `POST /api/v1/public/crm/forms/{formKey}/submit`: Unauthenticated submission handler with CSRF exemption.
  - **Public Landing Page (`PublicLeadFormPage.tsx`)**:
    - Direct shareable landing page at `/forms/{formKey}` with responsive inputs, submission handling, success states, and optional redirect support.
  - **Form Builder & Manager (`LeadFormsModal.tsx`)**:
    - Form listing with status toggle (`ACTIVE` / `PAUSED`), submission counters, and quick actions.
    - Interactive Form Builder: configure titles, descriptions, stages, success messages, and reorderable fields (standard presets + CRM custom fields).
    - Live Form Preview tab in the editor.
    - Embed & Share tab with direct links, responsive `<iframe>` code, and pure HTML+JS form snippets for embedding on Webflow, WordPress, Shopify, or custom websites.
    - Submissions viewer drawer to inspect submission history and linked contact profiles.
  - **CRM Leads Page Integration**: "Web Forms" button in `CrmLeadsPage.tsx`.

#### 4. Deal Document & Proposal Attachments
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V119__crm_deal_attachments.sql`**: Added `crm_deal_attachments` table linking `deal_id`, `media_id`, `filename`, `content_type`, `size_bytes`, `uploader_user_id`, and `created_at` with cascading deletes and indexes.
  - **Service & Ingestion Engine (`CrmDealAttachmentService`)**:
    - Validates company access and deal existence.
    - Records upload details and associates `media_id` with company.
    - Automatically logs `attached_document: <filename>` and `removed_document: <filename>` into `crm_deal_events` timeline.
  - **REST API (`CrmDealAttachmentController`)**:
    - `GET /api/v1/companies/{companyId}/crm/deals/{dealId}/attachments`
    - `POST /api/v1/companies/{companyId}/crm/deals/{dealId}/attachments`
    - `DELETE /api/v1/companies/{companyId}/crm/deals/{dealId}/attachments/{attachmentId}`
  - **Drag-and-Drop Upload & Document Management UI (`DealAttachmentsSection.tsx`)**:
    - Drag-and-drop file upload zone supporting proposals, pitch decks, PDFs, contracts, spreadsheets, presentations, and images up to 50MB.
    - Real-time percentage upload progress bar.
    - Document cards with tailored filetype icons (PDF, Spreadsheet, Presentation, Image, Archive, Document), file size, uploader attribution, and upload date.
    - 1-Click secure download/preview using signed URLs from `MediaService`.
    - Integrated directly into the `DealDetails` drawer in `CrmDealsPage.tsx`. Automatically refreshes deal activity history upon upload or deletion.

---

### 📈 Tier 2: Sales Acceleration & Pipeline Intelligence

#### 5. Sales Forecasting & Weighted Pipeline
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V120__crm_sales_quotas.sql`**: Added `crm_sales_quotas` table linking `company_id`, `user_id`, `period_type` (`MONTHLY`/`QUARTERLY`), `period_key`, `target_amount`, and `currency`.
  - **Service & Ingestion Engine (`CrmForecastService`)**:
    - Calculates stage probability weighting: $\sum (\text{deal amount} \times \text{win probability} \div 100)$.
    - Period resolution: `this_month`, `next_month`, `this_quarter`, `next_quarter`, `this_year`, `all_open`.
    - Total committed forecast metric: $\text{Closed-Won} + \text{Weighted Open Pipeline}$.
    - Target quota attainment % and projected attainment % calculations.
    - Close-date monthly cohort timeline aggregation.
    - Sales rep aggregation and leaderboard.
  - **REST API Endpoints**:
    - `GET /api/v1/companies/{companyId}/crm/forecast`: Comprehensive forecast payload with summary metrics, stages, cohorts, reps, and individual deals.
    - `GET /api/v1/companies/{companyId}/crm/quotas`: Fetches assigned sales quotas.
    - `PUT /api/v1/companies/{companyId}/crm/quotas`: Upserts rep target quotas with audit tracking.
  - **Interactive Executive Dashboard (`CrmForecastView.tsx`)**:
    - Integrated tab switcher in `CrmReportsPage.tsx` ("Sales Forecast & Quotas" vs "Activity Overview").
    - 5 Executive KPI cards: Total Forecast, Weighted Pipeline, Nominal Pipeline, Closed-Won Deals, and Quota Attainment progress bar.
    - Pipeline Stages & Probabilities Matrix with visual bars and face vs weighted values.
    - Expected Close Date Timeline cohorts with won vs open breakdown.
    - Sales Rep Quotas & Attainment Leaderboard with color-coded attainment progress bars.
    - Interactive "Assign Target Quota" modal for setting monthly/quarterly sales targets.
    - Opportunities in Forecast table displaying individual deal probabilities, nominal, and weighted revenue.

#### 6. Broadcast & Ad Attribution on Deals
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V121__crm_deal_campaign_attribution.sql`**: Added `attribution_channel`, `broadcast_message_id`, `ad_campaign_id`, `lead_form_id`, `utm_source`, `utm_medium`, and `utm_campaign` columns with foreign keys and performance indexes.
  - **Service & Ingestion Engine (`CrmAttributionService` & `CrmDealsService`)**:
    - Automatic multi-touch and first-touch attribution mapping on `crm_deals`.
    - Options resolver for Broadcast campaigns (WhatsApp/Email), Meta/Google Ad campaigns, and Inbound Lead Forms.
    - Closed-loop revenue calculation: Total Won Revenue vs. Marketing Attributed Won Revenue and Contribution Share %.
    - Channel-level breakdown with win rate %: Broadcast, Meta Ads, Google Ads, Web Forms, Direct Sales, Referral, Organic, Events, Partners.
    - Asset & Campaign Leaderboard aggregating won deals, open pipeline, and closed revenue per marketing asset.
  - **REST API Endpoints (`CrmSalesController`)**:
    - `GET /api/v1/companies/{companyId}/crm/reports/attribution`: Generates period-filtered attribution report.
    - `GET /api/v1/companies/{companyId}/crm/attribution/options`: Retrieves linked broadcast, ad campaign, and lead form options.
  - **Interactive Deal Management UI (`CrmDealsPage.tsx`)**:
    - Create/Edit Deal dialog with dedicated **Marketing Attribution & Source** section.
    - Dynamic campaign selector dropdowns reflecting selected channel.
    - UTM parameter tracking inputs.
    - Deal Details drawer displays the Marketing Attribution card with channel badge and linked campaign/form.
    - Kanban cards and list view table display marketing source badges (📢 Broadcast, 🎯 Meta Ads, 🌐 Google Ads, 📝 Web Form).
  - **Attribution & Campaign ROI Dashboard (`CrmAttributionView.tsx`)**:
    - Integrated as 3rd tab in **Sales Intelligence & Reports** (`CrmReportsPage.tsx`).
    - 4 Executive KPI cards: Total Won Revenue, Marketing Attributed Revenue, Contribution Share %, Attributed Deals.
    - Full Channel Attribution & Conversion table.
    - Campaign & Asset Leaderboard with subtabs (All, Broadcasts, Ad Campaigns, Web Forms).

#### 7. Rule-Based Lead Scoring & Temperature Grading
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V122__crm_lead_scoring.sql`**:
    - Added `score`, `temperature` (`HOT`, `WARM`, `COLD`), `score_breakdown` (JSONB), and `scored_at` columns with performance indexes to `crm_leads`.
    - Created `crm_lead_score_rules` table: supports rule conditions (`HAS_PHONE`, `HAS_EMAIL`, `HAS_ORGANIZATION`, `STAGE_MATCH`, `FORM_SUBMISSION`, `HAS_DEAL`, `COMMUNICATION_ACTIVITY`, `CUSTOM_FIELD_MATCH`), operator evaluations, point increments/penalties, and rule toggles.
    - Created `crm_lead_score_settings` table: configurable company-wide temperature thresholds (defaults: 70+ for 🔥 Hot, 30-69 for ⚡ Warm, <30 for ❄️ Cold).
  - **Scoring Engine Service (`CrmLeadScoringService.java`)**:
    - Automatic seeding of standard out-of-the-box scoring rules on company initialization.
    - Real-time scoring evaluation across 8 dimensions: identities, organization, stage, inbound form submissions, linked active deals, interaction history, and custom field values.
    - Automatic score recomputation triggered on lead creation (`enroll`), stage progression (`move`), and custom field updates (`updateCustomFields`).
    - Company-wide batch recalculation endpoint for instantly rescoring all leads after modifying rules or thresholds.
  - **REST API Endpoints (`CrmLeadScoringController.java` & `LeadController.java`)**:
    - `GET /api/v1/companies/{companyId}/crm/scoring/rules`: List all scoring rules.
    - `POST /api/v1/companies/{companyId}/crm/scoring/rules`: Create new scoring rule.
    - `PUT /api/v1/companies/{companyId}/crm/scoring/rules/{ruleId}`: Update rule or point weight.
    - `DELETE /api/v1/companies/{companyId}/crm/scoring/rules/{ruleId}`: Delete scoring rule.
    - `GET /api/v1/companies/{companyId}/crm/scoring/settings`: Fetch temperature thresholds.
    - `PUT /api/v1/companies/{companyId}/crm/scoring/settings`: Update hot/warm thresholds.
    - `POST /api/v1/companies/{companyId}/crm/scoring/recalculate`: Batch recalculate all leads.
    - `GET /api/v1/companies/{companyId}/crm/scoring/leads/{contactId}`: Fetch breakdown of points for an individual lead.
    - `GET /api/v1/companies/{companyId}/crm/leads?temperature=HOT|WARM|COLD`: Server-side temperature filtering.
  - **Interactive Management & Visual UI**:
    - **Score & Grade Column** in `CrmLeadsPage.tsx`: Interactive badges with temperature styling (🔥 Hot / ⚡ Warm / ❄️ Cold) and exact point total.
    - **Temperature Filtering**: One-click filter in `CrmLeadsPage.tsx` filter menu to immediately isolate Hot, Warm, or Cold prospects.
    - **Lead Scoring Config Dialog (`LeadScoringModal.tsx`)**:
      - Scoring Rules tab: Add/edit rule conditions, points, enable/disable toggles, rule deletion, and instant batch recalculation button.
      - Temperature Thresholds tab: Interactive visual preview spectrum with adjustable Hot and Warm point thresholds.
    - **Lead Grade & Score Breakdown Card (`LeadHistoryPanel.tsx`)**:
      - Displays temperature badge, total score, and itemized list of awarded points with badges and timestamps for why the lead scored those points.

---

### ⚡ Tier 3: Workflow Automation & Process

#### 8. Event-Driven CRM Automation Rules & Triggers
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V123__crm_automation_rules.sql`**:
    - Created `crm_automation_rules` table: supports trigger types (`LEAD_ENROLLED`, `LEAD_STAGE_CHANGED`, `FORM_SUBMITTED`, `DEAL_CREATED`, `DEAL_STAGE_CHANGED`), trigger condition configurations, action types (`CREATE_TASK`, `ASSIGN_OWNER`, `UPDATE_LEAD_STAGE`), execution counters, and timestamps.
    - Created `crm_automation_logs` table: audit trail tracking every automation execution with rule ID, trigger, entity ID, action, execution status (`SUCCESS`, `SKIPPED`, `FAILED`), execution summary details, and timestamp.
    - Created `crm_round_robin_state` table: tracks last assigned agent per company for fair, rotating lead/task distribution across active sales team members.
  - **Backend Automation Engine (`CrmAutomationService.java` & `CrmAutomationController.java`)**:
    - Centralized `fireEvent(companyId, triggerType, entityType, entityId, context)` engine with safe, non-blocking failure isolation.
    - Automatic token replacement in task templates (e.g. `{contactName}`, `{dealName}`, `{stage}`).
    - Built-in Round-Robin agent rotator utilizing active company members.
    - Automatic due date computation (`Instant.now().plus(dueDays, ChronoUnit.DAYS)`).
    - Task creation directly integrated into `crm_tasks` Kanban board via `CrmWorkService.createTask`.
    - Integrated lifecycle triggers in `LeadService.java` (`enroll` & `move`), `CrmDealsService.java` (`create` & `move`), and `CrmLeadFormService.java` (`submitPublicForm`).
    - REST API endpoints for rule CRUD, instant toggling (`PATCH /toggle`), and real-time execution log retrieval.
  - **Interactive Automation Manager (`CrmAutomationModal.tsx`)**:
    - **Automation Rules Tab**:
      - Rule cards displaying trigger condition pill, visual arrow `→`, action pill, active status toggle switch, execution counter, and edit/delete actions.
      - Interactive Rule Builder: select trigger event, configure stage conditions, choose automated action, set task instructions, priority, due days offset, and assignee routing (`ROUND_ROBIN` or `LEAD_OWNER`).
    - **Execution History & Logs Tab**:
      - Real-time audit table displaying execution timestamps, triggering rule, entity, status pill, and execution details.
    - Integrated top toolbar action button ("Automations" with ⚡ icon) in both `CrmLeadsPage.tsx` and `CrmDealsPage.tsx`.

#### 9. Meeting Scheduler & Calendar Booking Links
* **Status**: ✅ **COMPLETED & SHIPPED**
* **Delivered Capabilities**:
  - **Database Migration `V124__crm_meeting_scheduler.sql`**:
    - Created `crm_booking_types` table: host user ID, unique public `slug`, title, description, duration minutes (15, 30, 45, 60), location type (`GOOGLE_MEET`, `PHONE`, `IN_PERSON`, `CUSTOM`), working hours (`09:00` - `18:00`), active days of week array (`[1,2,3,4,5]`), buffer time after meetings, active status toggle, and created/updated timestamps.
    - Created `crm_meeting_bookings` table: booking type ID, host user ID, contact ID, guest name, guest email, guest phone, guest notes, start time, end time, timezone, status (`SCHEDULED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`), meeting link (e.g., Google Meet), cancellation reason, and timestamps.
  - **Public & Admin Security Configuration (`SecurityConfig.java`)**:
    - Exemption of `/api/v1/public/crm/bookings/**` from CSRF tokens and unauthenticated public permitAll access for guest clients.
  - **Backend Booking & Scheduling Engine (`CrmBookingService.java`, `CrmBookingController.java`, `CrmPublicBookingController.java`)**:
    - Full CRUD for host booking types and availability schedules.
    - Public booking page resolver: maps vanity URL `/book/:slug` to host and company branding.
    - Intelligent slot availability algorithm (`getAvailableSlots`): computes candidate time slots across operating days of the week, working hours, duration, buffer intervals, and suppresses already booked slots or conflicts.
    - Seamless Guest Booking Execution (`bookPublicMeeting`):
      - Verifies requested time slot availability with conflict locking.
      - Generates secure Google Meet conference links (`https://meet.google.com/xxx-yyyy-zzz`).
      - Resolves or automatically registers the prospect as a new CRM contact (`ContactService.createContact`) if not existing.
      - Automatically generates a CRM task (`CrmWorkService.createTask`) for the sales rep with priority and due date set to the meeting time.
      - Triggers audit logs and activity history.
  - **Public Calendly-Style Guest Booking Page (`PublicBookingPage.tsx`)**:
    - Accessible via `/book/:slug` (and registered in `AppRoutes.tsx`).
    - Modern, responsive dual-column scheduling interface:
      - Left column: Host profile avatar, name, meeting title, duration badge with clock icon, location badge with video icon, and meeting description.
      - Right column: 14-day horizontal date selector strip with day names, interactive time slot selection grid (morning, afternoon, evening slots).
      - Booking Details modal form: collects Guest Full Name, Email Address, Phone Number, and Meeting Agenda / Notes.
      - Instant confirmation screen: displays meeting confirmation card with formatted date/time, host name, Google Meet join button, and calendar reminder summary.
  - **Admin Meeting Scheduler Manager (`BookingManagerModal.tsx`)**:
    - **Booking Links Tab**:
      - Cards for all meeting types with duration, location badge, working hours, and active status toggle.
      - "Copy Link" button with instant clipboard feedback (`https://app.unifydesk.in/book/{slug}`).
      - "Create Booking Link" form: configure slug, title, description, duration, location type, working hours start/end, buffer minutes, and operating days.
    - **Scheduled Meetings Tab**:
      - Complete listing of upcoming and past booked meetings with guest name, email, phone, host rep, status pill, Google Meet link button, and cancellation dialog.
    - Integrated top toolbar action button ("Bookings" with 📅 calendar icon) in both `CrmLeadsPage.tsx` and `CrmDealsPage.tsx`.

---

### 🏢 Tier 4: Enterprise & Deep Vertical Features (Future / R3+)
- **Telephony & Call Recording** (Twilio integration with browser click-to-call and automatic call recordings).
- **Quote & Invoicing Generation** (Generate PDF quote from deal line items).
- **Territory Management** (State/region-based lead routing and data isolation).
- **AI Copilot / Zia Equivalent** (AI draft response, email summary of long customer threads, win-probability predictor).

---

## 4. Suggested Implementation Order

| Priority | Feature | Estimated Timeline | Key Advantage |
|:---:|:---|:---:|:---|
| **1st** | **WhatsApp 1-Click from Leads & Deals** | 1 - 2 Days | Leverages existing WhatsApp engine; instant WOW factor |
| **2nd** | **Multiple Pipelines & Custom Stages** | 2 - 3 Days | Essential for any business with multiple services or sales processes |
| **3rd** | **Deal Document / Contract Attachments** | 1 Day | Fast win using existing media storage |
| **4th** | **Web-to-Lead Capture Forms** | 2 Days | Complete inbound lead generation funnel |
| **5th** | **Sales Forecasting & Weighted Pipelines** | 2 Days | Transforms basic reporting into executive-ready forecasts |
