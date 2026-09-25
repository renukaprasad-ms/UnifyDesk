# Ad Center Gap Analysis, Product Architecture, and Implementation Roadmap

> Status: Product/engineering implementation specification  
> Audit date: 2026-09-24  
> Repository baseline: dev branch  
> Scope: Reachlyo Ad Center, Google Ads, Meta Ads, unified cross-provider advertising

## 1. Purpose

This document is the source of truth for the next major Ad Center phase.

It exists so future implementation work can be given to GPT/Codex feature-by-feature without repeating the complete product research every time.

The goal is NOT to clone Google Ads and Meta Ads Manager screen-for-screen. Both products change constantly and expose provider-specific workflows that cannot always be reproduced through public APIs.

The goal is to build Reachlyo as the layer above them:

- one business campaign
- one budget plan
- one audience definition
- one creative pack
- one conversion goal
- multiple provider deployments
- one reporting surface
- one recommendation/optimization layer
- provider-specific controls when required

The product should let advanced advertisers access provider-specific settings without forcing normal users to understand every Google and Meta object.

---

## 2. Current Reachlyo Ad Center

The current codebase is significantly more capable than the UI makes it appear.

### Shared Ad Center already implemented

- Provider registry and provider capabilities
- Provider connections
- Multiple ad accounts
- Managed/primary account selection
- Campaign persistence
- Ad-set/ad-group persistence
- Ad persistence
- Creative persistence
- Audience persistence
- Conversion persistence
- Keyword persistence
- Daily insight persistence
- Insight breakdown persistence
- Account billing
- Account health
- Recommendations
- Recommendation lifecycle
- Activity logging
- Provider sync
- Permissions/RBAC

Current frontend sections:

- Overview
- Campaigns
- Creatives
- Audiences
- Conversions
- Analytics
- Recommendations
- Merchant Center
- Billing
- Activity
- Settings

### Google Ads already implemented

Connection and infrastructure:

- OAuth
- account discovery
- MCC/customer hierarchy discovery
- stored manager context
- token refresh
- background synchronization
- per-account Redis synchronization locking

Campaigns:

- Search
- Performance Max
- Display
- Demand Gen
- Standard Shopping
- standalone Video synchronization/reporting

Campaign resources:

- budgets
- ad groups
- keywords
- responsive search ads
- responsive display ads
- Demand Gen video ads
- Demand Gen multi-asset ads
- Demand Gen carousel ads
- Shopping product ads
- listing group initialization

Performance Max:

- campaign creation
- initial asset group
- account asset reuse
- add/remove asset group assets
- search theme signals
- Customer Match audience signals
- image/logo/video assets
- portrait and landscape variants

Audience/data:

- audience sync
- Customer Match list creation
- Customer Match member upload through Data Manager
- identifier normalization/hashing
- consent/terms handling

Conversions:

- conversion action sync/create
- offline/enhanced conversion uploads
- GCLID/GBRAID/WBRAID support
- hashed first-party matching data

Merchant Center:

- accessible Merchant account discovery
- data source list/create/delete
- product listing
- product create/update/delete for API-managed sources
- product issue visibility

Reporting/operations:

- campaign/ad-group/ad metrics
- breakdown sync
- native Google recommendations
- apply/dismiss recommendation actions
- Reachlyo recommendations
- account health
- optimization score
- billing read

### Meta Ads already implemented

Connection and infrastructure:

- OAuth
- account discovery
- resource synchronization
- webhook endpoint
- token handling

Campaign hierarchy:

- campaigns
- ad sets
- ads
- creatives
- create/update/status/sync operations

Campaign creation currently supports:

- Awareness
- Traffic
- Engagement
- Leads
- App promotion
- Sales
- auction/reserved buying type
- campaign-level budget
- campaign bid strategy
- special ad categories

Ad-set creation currently supports:

- optimization goal
- billing event
- bid strategy
- bid amount
- daily/lifetime ad-set budget
- schedule
- destination
- country targeting
- age
- gender
- platform placement selection
- custom audience include/exclude
- pixel/custom conversion event

Creative creation currently supports:

- single image
- video
- carousel
- Facebook Page identity
- Instagram identity
- primary text
- headline
- description
- CTA
- destination URL
- URL tags

Audience/conversion:

- website audiences
- lookalike audiences
- Pixel creation/sync
- custom conversions
- account resources
- Page/Instagram/Pixel sync

Operations:

- analytics
- breakdowns
- billing
- account spend-cap mutation
- account health
- Reachlyo recommendations

---

## 3. Main Product Problem

The current product mostly exposes provider object hierarchies.

Meta flow:

~~~
Campaign
  -> Ad Set
      -> Creative
          -> Ad
~~~

Google Search flow:

~~~
Campaign
  -> Ad Group
      -> Keywords
      -> Responsive Search Ad
~~~

Google Display/Demand Gen follow similar provider-native workflows.

This is technically correct, but it creates a product problem:

A user who already understands all these concepts can simply use Google Ads and Meta Ads Manager.

Reachlyo needs to reduce work rather than reproduce it.

### Target value proposition

A customer should be able to express business intent:

> Generate qualified leads in Bengaluru this month. Budget: INR 50,000. Use this landing page, these images, and this offer.

Reachlyo should compile that into suitable Google and Meta deployments while keeping the advertiser in control.

---

# 4. Target Product Architecture

Introduce a Reachlyo-level object ABOVE provider campaigns.

~~~
Reachlyo Campaign
    |
    +-- Google deployment(s)
    |     +-- Search
    |     +-- Performance Max
    |     +-- Demand Gen
    |     +-- Shopping
    |
    +-- Meta deployment(s)
          +-- Campaign
              +-- Ad Set(s)
                  +-- Ads
~~~

Provider campaigns remain real provider entities. Reachlyo Campaign becomes the business-level orchestration entity.

## Recommended new domain model

### ReachlyoCampaign

Business-level campaign.

Suggested fields:

- id
- companyId
- name
- objective
- businessGoal
- landingPageUrl
- status
- startAt
- endAt
- currency
- totalBudgetMinor
- budgetAllocationMode
- createdBy
- createdAt
- updatedAt

Suggested statuses:

- DRAFT
- VALIDATING
- READY
- DEPLOYING
- PARTIALLY_DEPLOYED
- PAUSED
- ACTIVE
- PARTIAL_FAILURE
- FAILED
- COMPLETED
- ARCHIVED

### CampaignChannel

Represents one requested channel/deployment type.

Examples:

- GOOGLE_SEARCH
- GOOGLE_PERFORMANCE_MAX
- GOOGLE_DEMAND_GEN
- GOOGLE_DISPLAY
- GOOGLE_SHOPPING
- META_ADS

Fields:

- reachlyoCampaignId
- provider
- channelType
- enabled
- budgetAllocationMinor
- providerOverrideJson
- status

### ProviderDeployment

Maps Reachlyo objects to provider objects.

Fields:

- reachlyoCampaignId
- campaignChannelId
- provider
- adAccountId
- externalCampaignId
- localCampaignId
- providerStatus
- deploymentStatus
- lastErrorCode
- lastErrorMessage
- lastSyncedAt

### CampaignGoal

Normalized business outcomes.

Examples:

- PURCHASE
- LEAD
- QUALIFIED_LEAD
- SIGN_UP
- PHONE_CALL
- WHATSAPP_CONVERSATION
- APP_INSTALL
- TRAFFIC
- AWARENESS

### ConversionGoalMapping

Maps Reachlyo business outcomes to provider conversion resources.

Examples:

- Reachlyo LEAD -> Google conversion action
- Reachlyo LEAD -> Meta Pixel LEAD event
- Reachlyo PURCHASE -> Google purchase conversion
- Reachlyo PURCHASE -> Meta purchase event

### BudgetPlan

Stores:

- overall budget
- daily/total strategy
- provider allocations
- allocation rules
- optional min/max channel limits

### AudienceDefinition

Canonical Reachlyo audience.

Possible fields:

- locations
- languages
- age ranges
- gender where applicable
- customer lists
- retargeting conditions
- exclusions
- interests
- search intent
- provider-specific overrides

### AudienceMapping

Stores the provider representation and any unsupported/dropped fields.

Never silently discard an unsupported Reachlyo setting.

### CreativePack

Source creative owned by Reachlyo.

Fields can include:

- brand
- business name
- offer
- landing page
- primary message
- headlines
- descriptions
- CTAs
- images
- logos
- videos
- product references

### CreativeVariant

Provider/placement-specific output.

Examples:

- GOOGLE_RSA
- GOOGLE_DISPLAY
- GOOGLE_PMAX
- GOOGLE_DEMAND_GEN
- META_FEED
- META_STORY
- META_REEL
- META_CAROUSEL
- META_WHATSAPP_STATUS

### ProviderOverride

Advanced settings that should not pollute the canonical cross-provider model.

Examples:

- Google AI Max flags
- Google search network configuration
- Google bidding details
- Meta Advantage+ settings
- Meta placement positions
- provider-specific conversion configuration

### LaunchRun / LaunchStep

Persistent orchestration/audit records for remote API mutations.

A LaunchRun represents one attempt to deploy/update a Reachlyo campaign.

LaunchStep records:

- provider
- operation
- request id/idempotency key
- resource
- state
- provider response ID
- error
- startedAt
- completedAt

---

# 5. Unified Campaign Builder

Create a new default campaign creation experience.

Provider-native create modals can remain available as an Advanced mode.

## Step 1 - Goal

Ask:

- What are you trying to achieve?
- What is the primary conversion?

Examples:

- Sales
- Leads
- Traffic
- Awareness
- App promotion
- Local promotion
- Retargeting
- Product sales

Then choose the actual conversion:

- purchase
- lead
- qualified lead
- registration
- WhatsApp conversation
- phone call
- app install
- custom conversion

The goal should drive later recommendations.

## Step 2 - Channels

Allow:

- Google Search
- Google Performance Max
- Google Demand Gen
- Google Display
- Google Shopping
- Meta
- Recommended channels

The user should be able to choose both Google and Meta in one campaign.

Reachlyo should explain WHY a channel is suggested, not silently choose it.

## Step 3 - Budget and schedule

User enters:

- total budget
- campaign date range

Allocation modes:

- automatic recommendation
- percentage by channel
- fixed budget by channel

Example:

- total INR 50,000
- Google Search INR 15,000
- PMax INR 15,000
- Meta INR 20,000

Automatic allocation should initially be advisory. Do not automatically move money across providers without explicit user configuration.

## Step 4 - Audience

Canonical controls:

- country
- region/state
- city
- postal code if provider supports it
- radius
- languages
- age
- gender where applicable
- customer list
- retargeting
- exclusions
- business/customer segments

Provider-only settings should appear under Advanced Google or Advanced Meta.

## Step 5 - Creative Pack

User supplies once:

- website/landing page
- business name
- offer
- images
- logos
- videos
- main message
- CTA

Reachlyo creates/recommends variants for provider requirements.

Do NOT send one literal creative unchanged to every provider.

## Step 6 - Provider customization

Show provider-specific sections only if the user wants them.

Google examples:

- keywords
- negative keywords
- Search headlines/descriptions
- search themes
- AI Max
- assets/extensions
- bidding controls
- Performance Max URL controls

Meta examples:

- Advantage+ settings
- placement-specific assets
- detailed targeting
- Page/Instagram/WhatsApp identity
- catalog/product set
- creative automation

## Step 7 - Tracking

Map:

- Google conversion actions
- Meta Pixel/custom conversions
- offline/enhanced conversions
- UTMs
- Reachlyo lead/conversation events

## Step 8 - Review

Show:

- provider deployment plan
- expected provider objects
- all validation warnings
- unsupported mappings
- placement previews
- budget allocation
- tracking mappings

Examples:

- "Instagram Reels is missing a 9:16 creative."
- "Google Search requires at least three headlines."
- "Meta interest targeting has no direct Google equivalent."
- "Google Video campaign creation is not available through the API."
- "This Google account does not expose the required Merchant account."

## Step 9 - Safe launch

All remote objects must be created PAUSED first.

Only activate after all required creation/validation steps succeed.

Never leave a partially created provider campaign live.

---

# 6. Multi-provider Launch Orchestrator

Do not rely on a database transaction for Google/Meta mutations.

Remote systems cannot participate in the local Spring transaction.

Use a Saga/orchestration model.

~~~
Validate Reachlyo campaign
        |
Compile provider plans
        |
Validate capabilities
        |
Validate account/resource prerequisites
        |
Create Google objects PAUSED
        |
Create Meta objects PAUSED
        |
Verify provider objects
        |
Persist deployment mappings
        |
All required deployments valid?
        |
 YES -------------------- NO
 |                         |
Activate requested      Keep PAUSED
deployments             Mark partial failure
 |                       Show recovery actions
Done
~~~

Requirements:

- idempotency
- retryable launch steps
- provider request IDs stored
- partial failure visibility
- explicit retry
- no duplicated provider campaigns during retry
- audit trail
- safe resume after backend restart
- compensation only where safe
- otherwise leave remote resource PAUSED and surface it

---

# 7. Capability System Upgrade

The current high-level capability enum is too broad.

CAMPAIGN_MANAGEMENT does not tell the frontend whether an account/provider can use AI Max, Keyword Planner, WhatsApp Status, catalog ads, billing writes, etc.

Add fine-grained capability descriptors.

Possible capabilities:

## Shared

- CAMPAIGN_CREATE
- CAMPAIGN_UPDATE
- CAMPAIGN_STATUS
- CREATIVE_CREATE
- AUDIENCE_CREATE
- CONVERSION_CREATE
- ANALYTICS_READ
- RECOMMENDATIONS
- BILLING_READ

## Google

- GOOGLE_SEARCH
- GOOGLE_AI_MAX
- GOOGLE_PERFORMANCE_MAX
- GOOGLE_DISPLAY
- GOOGLE_DEMAND_GEN
- GOOGLE_SHOPPING
- GOOGLE_APP_CAMPAIGN
- GOOGLE_KEYWORD_PLANNER
- GOOGLE_FORECAST
- GOOGLE_EXPERIMENTS
- GOOGLE_ASSET_GENERATION
- GOOGLE_BID_SIMULATION
- GOOGLE_MERCHANT
- GOOGLE_CUSTOMER_MATCH
- GOOGLE_DATA_MANAGER
- GOOGLE_BILLING_WRITE_MONTHLY_INVOICE

## Meta

- META_DETAILED_TARGETING
- META_CUSTOM_AUDIENCE_CUSTOMER_LIST
- META_LOOKALIKE
- META_ADVANTAGE_AUDIENCE
- META_ADVANTAGE_PLACEMENTS
- META_ADVANTAGE_CREATIVE
- META_GENERATIVE_TEXT
- META_IMAGE_UNCROP
- META_BACKGROUND_GENERATION
- META_CATALOG
- META_EXISTING_POST
- META_PARTNERSHIP_ADS
- META_WHATSAPP_STATUS

Capability results should include:

- supported
- reason when unsupported
- account eligibility when applicable
- API/version requirement
- permission/scope requirement

---

# 8. Google Ads Missing Features

This section is the Google backlog.

## G-01 Search targeting completeness - P0

Status: **in progress**. B1 now covers geo/language/network/daypart creation in the unified Reachlyo flow. Devices, deeper audience/demographic criteria, observation-vs-targeting controls, exclusions, provider-native read/edit surfaces, and campaign-specific conversion-goal mutation remain.

Current Search campaign creation is too minimal.

Add:

- locations
  - countries
  - regions
  - cities
  - radius
- location presence/interest mode
- languages
- networks
- devices where supported
- ad schedules/dayparting
- audience criteria
- demographics where supported
- observation vs targeting
- start/end
- conversion goal selection

Add backend provider criteria abstractions instead of hardcoding everything in campaign DTOs.

Reference:

- Search campaigns: https://developers.google.com/google-ads/api/docs/campaigns/search-campaigns/getting-started
- Targeting criteria: https://developers.google.com/google-ads/api/docs/targeting/overview

## G-02 Negative keywords - P0

Status: **core management complete**. Reachlyo now supports ad-group negatives, campaign negatives, reusable Google shared negative keyword lists, provider-live read-through/sync, attach/detach, bulk entry, and unified-launch application. Search-term suggestions and the "search term -> add as negative" action remain intentionally coupled to G-03/B8 search-term reporting.

Missing/next integration:

- [x] campaign negative keywords
- [x] ad-group negative keywords where supported
- [x] shared negative keyword lists
- [x] attach/detach shared lists
- [x] sync/read existing negatives

Frontend:

- [x] bulk add
- suggestions — deliver with search-term reporting so suggestions are evidence-backed
- search-term -> add as negative action — deliver with G-03/B8

Reference:

- Search campaign best practices mention shared negative keyword lists:
  https://developers.google.com/google-ads/api/docs/campaigns/search-campaigns/getting-started

## G-03 Search term reporting - P1

Add:

- search terms
- matched keyword
- match type
- clicks
- impressions
- spend
- conversions
- value
- CPA/ROAS
- action to promote term to keyword
- action to exclude term

Must support AI Max reporting semantics as they evolve.

## G-04 AI Max for Search - P1

Add:

- enable AI Max
- search term matching controls
- text customization controls
- Final URL Expansion
- URL inclusions/exclusions
- brand inclusions/exclusions
- text guidelines/restrictions
- AI Max reporting
- migration state read

Current Google API docs describe AI Max as an optimization layer over Search campaigns rather than a separate campaign type.

Reference:

- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/getting-started
- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/ai-max-reporting
- https://developers.google.com/google-ads/api/docs/release-notes

## G-05 Keyword Planner - P1

Build a planning service.

Inputs:

- business URL
- seed keywords
- locations
- language
- network

Outputs:

- keyword ideas
- suggested groups/themes
- monthly search volume
- CPC/competition metrics exposed by API
- forecast metrics
- selectable keyword list

Recommended Reachlyo flow:

~~~
Website/Product
   -> keyword ideas
   -> historical metrics
   -> cluster/theme
   -> user selects
   -> forecast
   -> create Search campaign
~~~

Cache results because Google rate limits planning services and historical values do not change rapidly.

Reference:

- https://developers.google.com/google-ads/api/docs/keyword-planning/overview
- https://developers.google.com/google-ads/api/docs/keyword-planning/generate-keyword-ideas

## G-06 Google assets/extensions - P1

Build a complete reusable asset layer.

Include supported Google campaign assets such as:

- sitelinks
- callouts
- structured snippets
- images
- promotion assets
- price assets
- business information assets
- lead-form related assets where public API/account eligibility allows
- tracking templates
- final URL suffix/custom params

Support association at appropriate level:

- customer
- campaign
- ad group

Reference:

- https://developers.google.com/google-ads/api/docs/assets/overview
- https://developers.google.com/google-ads/api/docs/ads/upgraded-urls/supported-entities

## G-07 Google AI asset generation - P1

Investigate and implement eligible AssetGenerationService functions.

Potential UX:

- generate Search copy from landing page/business context
- generate headline alternatives
- generate descriptions
- allow user selection/editing
- log/generated-vs-user-supplied provenance
- never silently publish AI output

Reference starting point:

- Google Ads API reference and AssetGenerationService:
  https://developers.google.com/google-ads/api/reference/rpc/v25/AssetGenerationService

Availability and eligibility must be capability-checked before UI exposure.

## G-08 Performance Max completeness - P1

Existing PMax implementation is strong but add missing modern controls.

Add/review:

- campaign URL options
- tracking template
- custom URL parameters
- final URL suffix
- Final URL Expansion controls
- URL exclusions/inclusions when supported
- brand guidelines
- lifecycle/customer acquisition goals
- value multipliers where supported
- automated video crawl setting
- deeper asset strength/diagnostics
- asset group performance
- listing groups where relevant

Google v25.2 adds AssetGroup URL options and automated video crawl settings.

Reference:

- https://developers.google.com/google-ads/api/performance-max/getting-started
- https://developers.google.com/google-ads/api/performance-max/create-campaign
- https://developers.google.com/google-ads/api/docs/release-notes

## G-09 Demand Gen completeness - P1

Current creative coverage is good, but targeting/control coverage is incomplete.

Add:

- location controls
- languages
- audience configuration
- device/OS/network controls where exposed
- channel/inventory controls where exposed
- placement/channel reporting
- conversion goal selection
- total budget support where API permits
- exclusions

Always capability-check against current Google API version.

Reference:

- https://developers.google.com/google-ads/api/docs/campaigns/demand-gen/overview

## G-10 App campaigns - P1/P2

Reachlyo does not currently create App campaigns.

Research and implement supported App campaign flows.

Must distinguish:

- createable/current API capabilities
- legacy App campaign limitations
- UI-only provider functionality

Reference:

- https://developers.google.com/google-ads/api/docs/app-campaigns/overview

## G-11 Portfolio/shared bidding - P2

Research and implement where supported:

- portfolio bid strategies
- shared budgets
- assignment
- reporting
- compatibility validation by campaign type

Do not expose portfolio options to campaign types that do not support them.

## G-12 Experiments - P2

Create an experiment domain in Reachlyo.

Google supports multiple API experiment types including Search, Display, Video/Demand Gen, AI Max adoption, broad match adoption, asset optimization, PMax/Shopping comparison and others depending on version/account eligibility.

Lifecycle:

- setup
- schedule
- run
- report
- end/promote/graduate

Reference:

- https://developers.google.com/google-ads/api/docs/experiments/overview
- https://developers.google.com/google-ads/api/samples/create-experiment

## G-13 Bid/budget simulations and planning - P2

Add provider planning data when available:

- bid simulations
- budget simulations
- forecasts
- Performance Planner integrations where API/access allows

This should power recommendations, not act as guaranteed predicted results.

Never present forecasts as promises.

Reference starting point:

- https://developers.google.com/google-ads/api/docs/bid-simulations/overview

## G-14 Reporting expansion - P2

Add provider-specific analytical surfaces:

- search terms
- keywords
- assets
- asset groups
- landing pages
- placements
- auction insights where available
- change history
- conversion attribution fields
- experiment performance
- recommendation impact
- lifecycle/customer acquisition reporting
- AI Max reporting

Keep the current normalized reporting layer but allow provider-specific drill-down.

## G-15 Billing improvements - P2

Current Google billing is mostly read-only.

Google Ads API supports programmatic billing workflows for accounts configured for monthly invoicing.

Possible support:

- billing setups
- account budget proposals
- account budgets
- invoices

Do NOT attempt to reproduce provider payment-card management when the API does not allow it.

Reference:

- https://developers.google.com/google-ads/api/docs/billing/overview

---

# 9. Meta Ads Missing Features

Meta changes frequently. Every implementation must verify the active Graph/Marketing API version and account eligibility at implementation time.

The repo currently targets Meta Graph API v26.0.

## M-01 Detailed targeting - P0/P1

Current audience setup is too basic.

Add support for provider targeting search and selection:

- interests
- behaviors
- demographics where exposed
- education/work/life-event categories where exposed
- detailed targeting exclusions where available
- flexible/detailed targeting groups if supported in the active API

Do not hardcode old fields without verifying the current Marketing API version.

Reference:

- Meta Marketing API official workspace:
  https://www.postman.com/meta/facebook-marketing-api/overview
- Marketing API docs:
  https://developers.facebook.com/docs/marketing-apis/

## M-02 Geo targeting completeness - P0

Current creation mainly uses country codes.

Add:

- regions
- cities
- ZIP/postal where supported
- radius targeting
- location types
- provider search UI for valid geo IDs

Store canonical location plus provider mapping.

## M-03 Placement tree - P0

Current frontend mostly exposes platform-level placement.

Build a placement selector with actual supported placement positions.

Categories should be populated/capability-driven, not permanently hardcoded.

At minimum model:

- Facebook placements
- Instagram placements
- Messenger placements
- Audience Network placements
- emerging eligible placements such as WhatsApp Status where API/account supports them

Add placement-level creative requirements and previews.

## M-04 Advantage+ audience - P1

Add modern Meta audience automation controls.

Requirements:

- expose provider eligibility
- allow advertiser to provide audience suggestions/constraints where API supports
- clearly distinguish strict controls from suggestions/expansion
- never imply Reachlyo has tighter control than Meta actually honors

Verify fields against the current Marketing API version before coding.

## M-05 Advantage+ placements - P1

Provide:

- automatic/Advantage+ placements
- manual placement tree
- placement warnings
- placement-specific creative variant mapping

## M-06 Advantage+ creative - P1

Current creative creation is manual.

Add support for eligible creative features exposed by Marketing API.

Possible capabilities include:

- text generation/variations
- image expansion/uncropping
- background generation
- other current Advantage+ creative features exposed by the active Marketing API

Always require preview/approval before Reachlyo publishes AI-generated material unless the user explicitly configures automatic creative optimization.

Official Meta Postman collection:

- https://www.postman.com/meta/facebook-marketing-api/collection/r3touuy/mapi-generative-ai-features
- Text generation:
  https://www.postman.com/meta/facebook-marketing-api/folder/wd8whru/text-generation
- Background generation:
  https://www.postman.com/meta/facebook-marketing-api/folder/i5cscav/background-generation
- Image expansion examples:
  https://www.postman.com/meta/facebook-marketing-api/request/f4q2498/8-creating-single-image-ad-with-image-uncrop

## M-07 Creative variations - P1

Expand the creative model beyond one text/headline combination.

Add:

- multiple primary-text options
- multiple headlines
- multiple descriptions where API supports
- placement-specific media
- aspect-ratio variants
- variant preview
- provider-level generated variants
- creative performance analytics

## M-08 Existing Facebook/Instagram post ads - P1

Support reusing eligible existing content.

Meta's official Marketing API examples support ad creative creation using an existing object_story_id.

Reference:

- https://www.postman.com/meta/facebook-marketing-api/request/zakdxi8/createusingexistingcreative

UX:

- sync eligible existing posts
- preview
- choose existing post as creative source
- preserve provider restrictions

## M-09 Customer-list Custom Audiences - P1

Current CreateMetaAudienceRequest only handles website/lookalike configuration.

Add:

- customer list audience
- hashed identifiers
- consent handling
- upload job lifecycle
- audience status
- matching/availability status where exposed
- source labeling

This later allows Reachlyo contacts/customers to become first-party advertising audiences.

Privacy/consent requirements must be explicit.

## M-10 Engagement/App/Offline audiences - P1/P2

Research current API support and add eligible audience subtypes such as:

- engagement audiences
- app activity audiences
- offline/CRM audiences
- Page/Instagram/video engagement sources

Do not create one giant free-form JSON screen. Use typed forms based on audience type.

## M-11 Lookalike improvements - P1

Current lookalike model is basic.

Add:

- source audience selector
- target country/region
- similarity/ratio controls supported by current API
- audience size/status
- provider validation

## M-12 Catalog/Product Sets - P1

Reachlyo has strong Google Merchant support but no equivalent Meta catalog product system.

Add:

- catalog discovery
- catalog connection/resources
- product sets
- product sync/read
- catalog issues where API exposes them
- dynamic/catalog creative
- catalog sales campaign creation

Reference:

- Meta official generative/Marketing API collection catalog example:
  https://www.postman.com/meta/facebook-marketing-api/request/0w6p8rh/get-catalog-and-product-set
- Advantage+ Catalog campaign example:
  https://www.postman.com/meta/facebook-marketing-api/request/gs1zfe5/advantage-catalog-campaign

## M-13 WhatsApp Status placement - P1

Reachlyo already includes WhatsApp as a messaging channel, so WhatsApp ad destinations/placements are strategically important.

Research and implement current Marketing API support for WhatsApp Status advertising.

Requirements:

- capability/eligibility check
- required WhatsApp business identity mapping
- placement support
- media requirements
- CTA/destination validation
- carousel support if current API permits
- reporting breakdowns

Do NOT expose this based only on Graph version. Confirm the actual ad account/app eligibility and active Marketing API documentation.

## M-14 Partnership/creator ads - P2

Research current API support for:

- partnership ads
- creator identity
- eligible existing partnership content
- permissions/authorization

Creator discovery/contracting experiences may remain provider-hosted even when ad creation is available.

Keep API-supported ad execution separate from Marketplace-style discovery UX.

## M-15 Meta experiments / A-B testing - P2

Investigate the current supported Marketing API experiment/split-test surfaces.

Add only capabilities available through public API for our app/account level.

If Ads Manager exposes a flow without a public supported API, link the user to provider UI instead of simulating it.

## M-16 Meta automated rules - P2

Investigate provider automated rules API support and decide whether to:

1. synchronize/use provider-native rules, and/or
2. use Reachlyo's own cross-provider automation engine.

The Reachlyo rule engine is strategically more valuable because it can act on Google + Meta together.

---

# 10. Cross-provider Features Missing From Reachlyo

These are the biggest product differentiators.

## X-01 Reachlyo Campaign - P0

Implement the parent campaign domain described above.

Do this before continuing to add many isolated provider fields.

## X-02 Unified campaign wizard - P0

Implement the goal -> channels -> budget -> audience -> creative -> tracking -> review -> launch flow.

## X-03 Provider compiler - P0

Input:

- Reachlyo campaign
- selected channels
- canonical audience
- Creative Pack
- conversions
- budget

Output:

- validated provider deployment plans

A provider compiler MUST NOT make remote mutations.

It should produce an inspectable plan first.

Example:

~~~
Google Search deployment plan
- CampaignBudget
- Campaign
- Location criteria
- Language criteria
- Ad groups
- Keywords
- Negative keywords
- RSA
- Campaign assets
- Conversion goal mapping

Meta deployment plan
- Campaign
- Ad Set
- Targeting
- Pixel/custom conversion mapping
- Creative
- Ad
~~~

## X-04 Creative Pack - P0

One reusable source asset collection.

Important rule:

ONE source pack does NOT mean ONE identical provider creative.

Reachlyo must generate/map appropriate variants.

## X-05 Creative validation/previews - P0

Before launch:

- validate dimensions
- validate aspect ratio
- validate file size/type
- validate text constraints
- validate required number of assets
- validate identities
- generate provider previews when API supports previews
- flag missing placement variants

## X-06 Unified audience compiler - P0/P1

Canonical audience -> provider mappings.

Store mapping quality:

- EXACT
- APPROXIMATE
- PROVIDER_ONLY
- UNSUPPORTED

Never silently drop targeting.

## X-07 Unified conversion mapping - P0/P1

A campaign should select a business outcome once.

Map that outcome to provider measurement objects.

Eventually Reachlyo-owned messaging/CRM events should feed provider offline/enhanced conversion systems where lawful and configured.

## X-08 Cross-provider reporting - P1

Current normalized analytics is a strong base.

Add Reachlyo campaign reporting:

- total spend
- spend by provider/channel
- impressions
- clicks
- conversions
- revenue/value
- CPA
- ROAS
- provider share
- campaign trend
- creative performance
- audience performance

Provider-specific metrics should remain accessible.

## X-09 Cross-provider recommendations - P1

Recommendations should be evidence-based and actionable.

Examples:

- increase/decrease budget
- move budget between channels
- add negative keyword
- fix missing creative variant
- replace fatigued creative
- fix conversion tracking
- pause poor deployment
- test a new variation

Automatic changes must require explicit configuration.

## X-10 Automation Rules - P2

Create a Reachlyo rule engine.

Example rules:

~~~
IF Search term spend > INR 1000
AND conversions = 0
THEN suggest negative keyword

IF Meta CPA > INR 800 for 3 days
AND conversions >= 10
THEN recommend reducing Meta budget

IF Google ROAS > 3
AND Meta ROAS < 1.5
THEN recommend reallocating budget

IF Meta frequency rises
AND CTR falls materially
THEN recommend a fresh creative variant
~~~

Rule types:

- recommendation only
- approval required
- automatic within configured guardrails

Guardrails:

- daily budget movement limit
- min/max campaign budget
- cooldown
- minimum sample/conversion threshold
- rollback support where possible
- activity/audit record

## X-11 Unified first-party audience system - P1/P2

Long-term strategic feature.

Potential Reachlyo segments:

- clicked an ad but did not convert
- opened WhatsApp conversation but did not purchase
- purchased product A
- inactive customer for 90 days
- email/SMS responder
- high-value customer
- qualified lead

Compile eligible segments to:

- Google Customer Match
- Meta Custom Audiences

Consent/privacy controls are mandatory.

## X-12 Full-funnel attribution - P2

Long-term target:

~~~
Ad
 -> Click
 -> Landing page
 -> Lead
 -> WhatsApp / Email / SMS
 -> Inbox conversation
 -> Sales action
 -> Purchase
 -> Offline conversion feedback
 -> Provider optimization
~~~

This is a key Reachlyo differentiator because Reachlyo can observe channels that Google or Meta do not individually own.

---

# 11. Provider/API Limitations

Do not promise complete provider UI parity.

## Google standalone Video creation

Google Ads API can report/fetch Video campaigns but does not provide normal creation/update parity for standalone Video campaigns.

Keep current Reachlyo behavior:

- synchronize
- report
- show read-only state
- use Demand Gen/other API-supported YouTube inventory for programmatic creation

Reference:

- https://developers.google.com/google-ads/api/docs/video/overview

## Google billing

Programmatic billing management requires monthly invoicing.

Normal consumer payment method/card management should remain provider-hosted.

Reference:

- https://developers.google.com/google-ads/api/docs/billing/overview

## Account eligibility

Many provider features depend on:

- account type
- account history
- country
- billing setup
- API access level
- permissions
- provider rollout
- product eligibility

Therefore every advanced feature needs capability/eligibility detection.

## Meta fast-changing fields

Marketing API fields are removed/deprecated regularly.

Rules:

- verify active Graph version before implementing
- do not copy stale blog snippets
- prefer Meta developer docs and Meta official Postman workspace
- isolate raw Meta request models inside the Meta provider adapter
- do not leak Meta JSON into shared domain objects

---

# 12. UI/UX Changes

## Keep current Ad Center sections

The existing sections are useful for management and expert workflows.

Do NOT delete them.

Instead introduce a primary CTA:

**Create Reachlyo Campaign**

And an advanced alternative:

**Create directly in provider**

## Campaign list

Show Reachlyo campaigns first.

Suggested columns/cards:

- Campaign
- Goal
- Channels
- Status
- Budget
- Spend
- Conversions
- CPA
- ROAS
- Warnings

Expandable provider deployments:

- Google Search
- Google PMax
- Meta

Each shows:

- provider status
- effective status
- provider campaign ID
- spend
- conversions
- issues

## Campaign details

Tabs:

- Overview
- Channels
- Audience
- Creative
- Tracking
- Analytics
- Recommendations
- Activity
- Settings

Provider-specific panels appear within Channels/Advanced.

## Advanced provider mode

Expert users must still be able to edit detailed provider settings.

Do not reduce Reachlyo to a simplified-only tool.

---

# 13. Backend Package Direction

Suggested structure:

~~~
com.messaging.ads
  /campaign
    /domain
    /service
    /repository
    /controller

  /orchestration
    CampaignCompiler
    CampaignValidationService
    CampaignLaunchService
    CampaignLaunchWorker
    LaunchRecoveryService

  /creative
    CreativePackService
    CreativeVariantService
    CreativeValidationService

  /audience
    CanonicalAudienceService
    AudienceMappingService

  /conversion
    ConversionGoalMappingService

  /provider
    AdsProvider
    AdsProviderRegistry
    ProviderCapabilities
    ProviderCampaignCompiler

  /google
    ...

  /meta
    ...
~~~

Do not perform a giant destructive package refactor immediately.

Add new layers around existing Google/Meta services and migrate gradually.

---

# 14. Provider Compiler Contract

Recommended conceptual interface:

~~~
ProviderCampaignPlan compile(
    ReachlyoCampaign campaign,
    CampaignChannel channel,
    AdAccount account,
    CreativePack creative,
    AudienceDefinition audience,
    ConversionGoalMapping conversion
)

ProviderValidationResult validate(ProviderCampaignPlan plan)

ProviderDeploymentResult deployPaused(ProviderCampaignPlan plan)

ProviderDeploymentResult activate(ProviderDeployment deployment)
~~~

The plan must be serializable/persistable for debugging.

The compiler should make business intent explicit rather than hiding assumptions.

---

# 15. Reliability Requirements

Every remote mutation path must support:

- provider request/error ID capture
- idempotency strategy
- retry classification
- transient vs permanent failure
- safe replay
- timeouts
- rate-limit handling
- audit/activity logging
- token refresh handling
- permission errors
- provider API-version errors
- partial failure
- user-visible remediation message

Never log:

- OAuth access tokens
- refresh tokens
- raw customer-list PII
- secrets
- unredacted provider credentials

---

# 16. Data Privacy Requirements

Customer/audience ingestion is sensitive.

Requirements:

- require appropriate user confirmation/consent
- hash identifiers when provider requires hashing
- do not persist raw upload identifiers unless the product explicitly needs them and policy allows it
- encrypt sensitive stored data
- track audience source/provenance
- support deletion
- maintain company isolation
- enforce RBAC
- document provider terms confirmation where required

Existing Google Customer Match hashing behavior should be preserved.

---

# 17. Testing Requirements

Every implementation task must include tests.

## Unit

- compiler mapping
- validation
- money conversion
- provider enums
- eligibility
- failure classification

## Provider service tests

Mock HTTP/API responses for:

- success
- validation failure
- permissions
- expired token
- account ineligible
- partial failure
- rate limit
- provider server error

## Orchestrator

Test:

- Google success + Meta success
- Google fail before Meta
- Google success + Meta fail
- retry after partial failure
- repeated launch request
- activation failure
- backend restart/resume

## Frontend

Test:

- provider capabilities
- unsupported settings
- wizard persistence
- review plan
- validation warnings
- launch progress
- partial failure/retry

---

# 18. Recommended Implementation Order

Do NOT attempt all provider parity features at once.

## Phase A - Foundation / P0

A1. [x] Reachlyo Campaign domain — implemented on `feature/ad-center-unified-campaign-foundation`  
A2. [x] CampaignChannel + ProviderDeployment — implemented foundation; remote launch wiring remains in A8-A10  
A3. [x] BudgetPlan — implemented with automatic/fixed/percentage allocation modes  
A4. [x] CreativePack + CreativeVariant — company media-backed source packs and provider/channel-specific variants  
A5. [x] AudienceDefinition + mapping status — canonical audience definitions with compiler-owned EXACT/APPROXIMATE/PROVIDER_ONLY/UNSUPPORTED mappings  
A6. [x] ConversionGoalMapping — campaign business goal mapped to validated provider conversion resources or standard events  
A7. [x] Provider capability model v2 — feature-level provider/account support, API version, scopes, eligibility and planned/read-only states  
A8. [x] Provider campaign compiler contract — side-effect-free Google/Meta plan compilation with budget, capability, creative, audience and conversion preflight  
A9. [x] LaunchRun/LaunchStep — persistent idempotent run/step audit state with retry metadata and provider resource IDs  
A10. [x] paused multi-provider launch orchestrator — resumable Saga with Google Search + Meta paused-first executors; unsupported channel executors are blocked during preflight before remote mutation  
A11. [x] unified campaign wizard — persisted goal-first flow for channels/accounts, shared budget, canonical creative, audience, provider-only settings and conversion mappings  
A12. [x] provider plan/review UI — side-effect-free per-channel review of ordered operations, capabilities, budget, mapping quality, warnings and paused-resource creation  
A13. [x] partial failure/retry UI — persistent launch history with validation issues, per-step status/attempts/provider IDs and failed-step retry controls

Completion condition:

A user can create one Reachlyo Campaign and deploy a simple valid campaign to Google + Meta in paused state, review it, then activate it.

## Phase B - Core parity gaps / P0-P1

B1. [x] Google geo/language/network/daypart targeting — canonical country/region/city/postal/proximity + language mapping, Search Partners/Display expansion, presence mode, quarter-hour dayparting, provider preflight and idempotent campaign-criterion execution  
B2. [x] Google negative keywords/shared negatives — ad-group negative create/remove + sync, campaign negatives with bulk add/remove, live reusable shared-list discovery/member management/attach-detach, unified campaign selection, preflight validation and idempotent launch execution  
B3. Meta detailed geo targeting  
B4. Meta proper placement tree  
B5. placement-specific Creative Pack variants  
B6. unified conversion mapping  
B7. cross-provider campaign analytics  
B8. search-term reporting  
B9. campaign-level recommendation improvements

## Phase C - Modern Google / P1

C1. AI Max for Search  
C2. Keyword Planner  
C3. forecasts  
C4. reusable Google campaign assets/extensions  
C5. PMax URL/automation updates  
C6. Google AI asset generation where eligible  
C7. Demand Gen targeting completeness  
C8. App campaigns  
C9. richer provider reporting

## Phase D - Modern Meta / P1

D1. customer-list Custom Audiences  
D2. Advantage+ audience controls  
D3. Advantage+ placements  
D4. Advantage+ creative  
D5. generative text  
D6. image uncrop/expansion  
D7. background generation  
D8. existing-post ads  
D9. Meta Catalog/product sets  
D10. WhatsApp Status ads where eligible

## Phase E - Optimization / P2

E1. Google experiments  
E2. Meta experiments if API-supported  
E3. Reachlyo experiments abstraction  
E4. automation rules  
E5. bid/budget simulation  
E6. cross-provider budget recommendations  
E7. first-party audience builder  
E8. full-funnel attribution  
E9. lifecycle/retargeting automations

---

# 19. Definition of Done for Any New Provider Feature

A task is NOT done because one API endpoint works.

Every feature must include:

- provider research verified against current API version
- capability detection
- backend request/response models
- provider service
- validation
- persistence if the data must survive sync
- activity/audit entry
- error mapping
- RBAC
- frontend UI
- loading state
- empty state
- error state
- mobile responsiveness
- sync/read-back where appropriate
- tests
- docs update
- no token/PII logging
- compatibility with existing provider-native flows

If provider API does not support the feature:

- do not fake it
- mark it provider-hosted/read-only
- explain it in the UI
- provide a provider link when useful

---

# 20. Official/Primary Reference Resources

Use these before implementing a feature. Always confirm API version and deprecations again at coding time.

## Google Ads

Main documentation:

- https://developers.google.com/google-ads/api/docs/start
- https://developers.google.com/google-ads/api/docs/release-notes
- https://developers.google.com/google-ads/api/reference/rpc/v25/overview

Search:

- https://developers.google.com/google-ads/api/docs/campaigns/search-campaigns/getting-started

AI Max:

- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/getting-started
- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/ai-max-reporting

Keyword planning:

- https://developers.google.com/google-ads/api/docs/keyword-planning/overview
- https://developers.google.com/google-ads/api/docs/keyword-planning/generate-keyword-ideas

Performance Max:

- https://developers.google.com/google-ads/api/performance-max/getting-started
- https://developers.google.com/google-ads/api/performance-max/create-campaign

Demand Gen:

- https://developers.google.com/google-ads/api/docs/campaigns/demand-gen/overview

App campaigns:

- https://developers.google.com/google-ads/api/docs/app-campaigns/overview

Experiments:

- https://developers.google.com/google-ads/api/docs/experiments/overview
- https://developers.google.com/google-ads/api/samples/create-experiment

Billing:

- https://developers.google.com/google-ads/api/docs/billing/overview

Video API limitations:

- https://developers.google.com/google-ads/api/docs/video/overview

Keyword/bid planning:

- https://developers.google.com/google-ads/api/docs/bid-simulations/overview

Assets:

- https://developers.google.com/google-ads/api/docs/assets/overview

Data Manager:

- https://developers.google.com/data-manager/api

Merchant API:

- https://developers.google.com/merchant/api

## Meta Marketing API

Start/current docs:

- https://developers.facebook.com/docs/marketing-apis/
- https://developers.facebook.com/docs/marketing-apis/get-started
- https://developers.facebook.com/docs/marketing-apis/overview

Official Meta Marketing API Postman workspace:

- https://www.postman.com/meta/facebook-marketing-api/overview

Official Meta generative creative collection:

- https://www.postman.com/meta/facebook-marketing-api/collection/r3touuy/mapi-generative-ai-features

Text generation examples:

- https://www.postman.com/meta/facebook-marketing-api/folder/wd8whru/text-generation

Background generation examples:

- https://www.postman.com/meta/facebook-marketing-api/folder/i5cscav/background-generation

Image expansion/uncropping example:

- https://www.postman.com/meta/facebook-marketing-api/request/f4q2498/8-creating-single-image-ad-with-image-uncrop

Existing-post creative example:

- https://www.postman.com/meta/facebook-marketing-api/request/zakdxi8/createusingexistingcreative

Catalog/product set example:

- https://www.postman.com/meta/facebook-marketing-api/request/0w6p8rh/get-catalog-and-product-set

Advantage+ Catalog campaign example:

- https://www.postman.com/meta/facebook-marketing-api/request/gs1zfe5/advantage-catalog-campaign

Important:

Meta official Postman requests can contain example/internal-looking base URLs or example values. Use them to understand request structure, but production code must use the documented public Graph API endpoint/version configured by Reachlyo.

---

# 21. GPT/Codex Task Instructions

When giving this document to GPT/Codex, do not ask:

> Implement everything in this roadmap.

Use one bounded slice at a time.

## Required prompt pattern

Use:

~~~
Read docs/ad-center-gap-analysis-and-roadmap.md and inspect the current dev branch.

Implement <TASK ID + task name> only.

Requirements:
1. Treat the roadmap as product/architecture guidance, but verify the provider's current official API documentation before coding.
2. Reuse current Ad Center entities/services where appropriate; do not duplicate existing functionality.
3. Preserve current Meta/Google direct-provider flows.
4. Do not fake a provider feature that the API cannot support.
5. Add capability/eligibility detection when needed.
6. Create provider objects PAUSED by default for creation flows.
7. Add backend validation and provider error mapping.
8. Add/update frontend UI with loading/error/empty states and mobile responsiveness.
9. Add unit/provider tests.
10. Update this roadmap status if the feature is completed.
11. Do not make unrelated refactors.
12. Run backend/frontend tests/builds that are relevant to this slice and fix introduced failures.

Before coding:
- inspect the existing implementation
- list what is already present
- identify exactly what will change
- verify current official provider docs

Then implement the slice end-to-end.
~~~

## Example first task

~~~
Read docs/ad-center-gap-analysis-and-roadmap.md.

Start Phase A with A1-A3 only:
- ReachlyoCampaign
- CampaignChannel + ProviderDeployment
- BudgetPlan

Do not build the launch orchestrator yet.

Requirements:
- create entities/migrations/repositories/services/controllers/DTOs
- company isolation + IAM
- Snowflake IDs using existing project conventions
- normalized enums
- no provider-specific fields in ReachlyoCampaign
- ProviderDeployment may reference existing local AdCampaign after deployment later
- add tests
- add frontend types/API only if needed for CRUD
- preserve all existing Ad Center behavior
- update roadmap status
~~~

Then continue with another prompt for A4-A6, then A7-A10, then the wizard.

---

# 22. Architecture Guardrails for Future GPT Work

Any coding agent working from this document must follow these rules.

1. Do not replace working Google/Meta services without a concrete reason.
2. Build the Reachlyo orchestration layer around existing adapters.
3. Keep provider-specific configuration in provider modules/overrides.
4. Do not put dozens of nullable Google/Meta fields into shared entities.
5. Always create new provider campaigns PAUSED unless a specific safe flow explicitly says otherwise.
6. Remote API writes are not part of a local DB transaction; use orchestration.
7. Every retryable provider mutation must have an idempotency/duplicate-prevention strategy.
8. Do not silently drop unsupported canonical settings.
9. Store enough information to explain compilation decisions to users.
10. Prefer official Google/Meta docs over third-party tutorials.
11. Verify provider API version at implementation time.
12. Provider UI parity is not the product objective.
13. The default product experience is business-goal first.
14. Expert/provider-native controls must remain available.
15. Reachlyo should recommend; advertisers retain control over material campaign decisions and spend.

---

# 23. Current Priority Summary

The current backend does NOT need to be thrown away.

The highest-value next step is not another isolated Google or Meta modal.

Build this foundation:

~~~
Reachlyo Campaign
        |
Campaign Builder
        |
Creative Pack + Audience + Conversion Goal
        |
Provider Compiler
        |
Google Plan + Meta Plan
        |
Paused Deployment Saga
        |
Review
        |
Activation
        |
Unified Analytics + Recommendations
~~~

Once this exists, every missing provider feature in this document becomes a capability plugged into the same campaign system instead of another disconnected workflow.

That is how Reachlyo moves from an alternative interface for provider APIs into a cross-channel marketing operating system.
