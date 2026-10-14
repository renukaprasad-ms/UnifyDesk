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
| **Web-to-Lead Capture Forms** | ❌ **Missing** | Web Forms builder | Power Pages / Portals | **Tier 1 (High Priority)** |
| **Deal Document / File Attachments** | ❌ **Missing** | Native attachments | SharePoint / OneDrive | **Tier 1 (High Priority)** |
| **Sales Forecasting & Weighted Pipelines** | ❌ **Basic summary only** | Native forecasting | AI-driven forecasting | **Tier 2 (Medium-High)** |
| **Campaign & Broadcast Attribution** | ❌ **Missing** | Campaigns module | Marketing Insights | **Tier 2 (Medium-High - Differentiator)** |
| **Rule-Based Lead Scoring** | ❌ **Missing** | Rule scoring / Zia | Predictive Scoring | **Tier 2 (Medium)** |
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
* **Why it matters**: Top-of-funnel acquisition is currently manual. Users must import contacts or manually create leads.
* **What needs to be built**:
  - Form builder or public submission endpoint: `POST /api/v1/public/forms/{formKey}/submit`.
  - Maps incoming form submissions directly to `contacts` + `crm_leads` + optional custom fields.
  - Embeddable HTML/iframe snippet for landing pages or Webflow/WordPress sites.
  - Auto-tagging and source assignment (`source = 'WEB_FORM'`).
* **Effort**: Medium (~2 days) | **Impact**: Closes the loop from website visitor → Qualified CRM Lead.

#### 4. Deal Document & Proposal Attachments
* **Why it matters**: Almost every B2B sales opportunity requires attaching proposals, pitch decks, scopes of work, or signed contracts.
* **What needs to be built**:
  - `crm_deal_attachments` table linking `deal_id`, `media_id` (using existing UnifyDesk media storage), filename, size, and uploader.
  - Drag-and-drop file upload zone in `DealDetails` panel.
  - Download and preview capabilities.
* **Effort**: Low (~1 day) | **Impact**: High utility for deal management.

---

### 📈 Tier 2: Sales Acceleration & Pipeline Intelligence

#### 5. Sales Forecasting & Weighted Pipeline
* **Current gap**: Reports show total open amount, won amount, and lost amount.
* **What to add**:
  - Stage probability weighting (e.g., `PROPOSAL` = 50%, `NEGOTIATION` = 80%).
  - Weighted pipeline forecast: $\sum (\text{deal amount} \times \text{stage probability})$.
  - Month/Quarter close date filters ("Expected close this month vs. next month").
  - Target/Quota progress bar per sales rep.

#### 6. Broadcast & Ad Attribution on Deals
* **Current gap**: Contacts have sources, but Deals don't show which marketing campaign or ad generated the revenue.
* **What to add**:
  - `campaign_id` (linking to Broadcast campaigns) and `ad_campaign_id` (linking to Ad Center) on `crm_deals`.
  - When a lead is converted from a broadcast interaction or ad lead form, automatically stamp the deal.
  - ROI report: "Campaign Summer Sale generated ₹12,50,000 in Closed-Won Deals".

#### 7. Rule-Based Lead Scoring
* **Current gap**: Sales reps must manually guess which leads are hot or cold.
* **What to add**:
  - Configurable scoring rules (e.g., +20 pts if email opened, +30 pts if replied on WhatsApp, +10 pts if visited website, +15 pts if budget > ₹5,00,000).
  - Visual score badge on Lead cards (🔥 Hot, ⚡ Warm, ❄️ Cold).

---

### ⚡ Tier 3: Workflow Automation & Process

#### 8. Event-Driven CRM Automation Rules
* **What to add**:
  - "When deal moves to `WON` → automatically create task 'Onboard client' assigned to account manager".
  - "When new lead created from web form → send WhatsApp greeting template + notify sales team".
  - Round-robin lead assignment among active sales agents.

#### 9. Meeting Scheduler / Calendar Sync
* **What to add**:
  - Shareable booking link (like Calendly / Zoho Bookings) connected to Google Calendar / Outlook.
  - Booked meetings automatically create calendar events + CRM activity logs.

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
