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

Status: **complete for the current verified Google Search API scope**. B1 covers geo/language/network/daypart creation in the unified Reachlyo flow, while the Ads completion branch adds exact age/gender buckets; parental-status and household-income include/exclude criteria; provider-backed Search-compatible detailed/extended demographics; DESKTOP/MOBILE/TABLET bid modifiers/exclusions; provider-backed Search-eligible User List include/exclude; explicit OBSERVATION vs TARGETING semantics; provider-backed Affinity and In-market USER_INTEREST discovery, validation, launch, exclusions, and native read/edit; retry-safe provider mutations; and native campaign/ad-group read/edit surfaces that preserve unmapped/provider-only criteria and unrelated target restrictions. Google Custom segments are deliberately not exposed for Search because Google currently marks Custom segments unavailable for Search campaigns. Campaign-specific conversion-goal mutation is already handled by the unified conversion mapping launch path.

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

Status: **complete for the current Search scope**. Reachlyo supports ad-group negatives, campaign negatives, reusable Google shared negative keyword lists, provider-live read-through/sync, attach/detach, bulk entry, unified-launch application, evidence-backed search-term review candidates, and direct search-term exclusion at ad-group or campaign level.

Missing/next integration:

- [x] campaign negative keywords
- [x] ad-group negative keywords where supported
- [x] shared negative keyword lists
- [x] attach/detach shared lists
- [x] sync/read existing negatives

Frontend:

- [x] bulk add
- [x] evidence-backed review candidates from search-term performance
- [x] search-term -> add as ad-group or campaign negative action

Reference:

- Search campaign best practices mention shared negative keyword lists:
  https://developers.google.com/google-ads/api/docs/campaigns/search-campaigns/getting-started

## G-03 Search term reporting - P1

Status: **core Search reporting complete**. Reachlyo now queries Google's current `search_term_view` for managed Search campaigns with a validated 90-day date range and exposes search term, matched keyword, matched keyword match type, Search-term match type, Google targeting status, AI Max/search match source, ad group, impressions, clicks, spend, conversions, conversion value, CPA and ROAS. The provider-native campaign page includes filtering, zero-conversion review candidates, and direct actions to promote a term to an ad-group keyword or exclude it at ad-group/campaign scope. Exact is the default mutation match type to minimize accidental over-blocking.

Current AI Max semantics are carried through `segments.search_term_match_source`, including keywordless and AI Max broad-match attribution where Google returns it. The more granular AI Max search-term/ad/headline/landing-page audit view remains part of C1 together with AI Max controls and URL/text automation.

Implemented:

- [x] search terms
- [x] matched keyword
- [x] match type
- [x] clicks
- [x] impressions
- [x] spend
- [x] conversions
- [x] value
- [x] CPA/ROAS
- [x] action to promote term to keyword
- [x] action to exclude term at ad-group or campaign scope
- [x] AI Max match-source attribution in the standard Search-term report

Reference:

- https://developers.google.com/google-ads/api/fields/v25/search_term_view
- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/ai-max-reporting

## G-04 AI Max for Search - P1

Status: **complete for the unified Google Search flow**. AI Max remains an advanced provider override on a normal Search campaign rather than a separate Reachlyo channel type.

Implemented:

- [x] campaign-level AI Max enablement through `campaign.ai_max_setting.enable_ai_max`
- [x] ad-group search-term matching control through `ai_max_ad_group_setting.disable_search_term_matching`
- [x] explicit compiler warning when individual keyword match types will be overridden by AI Max matching
- [x] text customization through `TEXT_ASSET_AUTOMATION`
- [x] Final URL Expansion through `FINAL_URL_EXPANSION_TEXT_ASSET_AUTOMATION`
- [x] URL exclusions as named negative campaign `WEBPAGE` criteria with retry-safe reuse
- [x] URL inclusions through reusable `PAGE_FEED` assets + AssetSet + CampaignAssetSet + positive ad-group `WEBPAGE` criteria
- [x] verified brand suggestions through Google's BrandSuggestionService
- [x] campaign-level brand inclusions/exclusions through reusable `BRANDS` SharedSets and positive/negative `brand_list` criteria
- [x] generated-text term exclusions using Google's current 25-item / 30-character limits
- [x] messaging restrictions using Google's current 40-item / 300-character limits and `RESTRICTION_BASED_EXCLUSION`
- [x] standard Search-term attribution via `segments.search_term_match_source`
- [x] dedicated `ai_max_search_term_ad_combination_view` transparency reporting for search term + headline + landing page with spend/conversion metrics
- [x] provider read-back for AI Max enablement, bundling state, asset automation, text guidelines, ad-group matching state, and Google ACA/broad-match migration timestamps
- [x] capability exposure, launch preflight, Saga operations, activity audit, responsive UI, and tests

URL inclusion configuration intentionally requires text customization, Final URL Expansion, and AI Max search-term matching to remain enabled, matching Google's current page-feed inclusion model. Brand selection stores the verified provider brand ID plus human-readable name/URLs in the provider override; users are never asked to paste Commercial Knowledge Graph IDs manually.

Current Google API docs describe AI Max as an optimization layer over Search campaigns rather than a separate campaign type. Provider migration timestamps are read-only state and are surfaced through synchronized provider payloads rather than mutated by Reachlyo.

Reference:

- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/getting-started
- https://developers.google.com/google-ads/api/docs/campaigns/ai-max-for-search-campaigns/ai-max-reporting
- https://developers.google.com/google-ads/api/docs/targeting/shared-sets
- https://developers.google.com/google-ads/api/reference/rpc/v25/BrandSuggestionService
- https://developers.google.com/google-ads/api/docs/release-notes

## G-05 Keyword Planner - P1

Status: **complete for C2 and C3**. The unified Google Search audience step now includes Keyword Planner discovery, historical metrics, and planless keyword forecasts backed by the current v25 `KeywordPlanIdeaService`.

Implemented inputs:

- [x] business/page URL
- [x] up to 20 seed keywords for idea generation
- [x] URL-only, keyword-only, keyword+URL, and entire-site seed modes
- [x] canonical campaign country/region/city/postal targets, resolved to Google geo target constants
- [x] one planning language at a time, resolved from targetable Google language constants
- [x] Google Search vs Google Search + Search Partners
- [x] explicit rejection of unsupported proximity/radius planning instead of silently dropping it

Implemented outputs:

- [x] keyword ideas
- [x] Google concept annotations used as pre-campaign theme labels
- [x] average monthly searches
- [x] monthly search-volume history
- [x] competition level and competition index
- [x] low/high top-of-page bid ranges
- [x] average CPC when Google returns it
- [x] close variants
- [x] pagination / load-more
- [x] selectable keyword list merged into the canonical Search-intent field
- [x] historical-metrics analysis for entered keywords

Forecasting (C3):

- [x] planless Search keyword forecasts through `GenerateKeywordForecastMetrics`
- [x] future forecast period validation with Google's one-year horizon
- [x] BROAD / PHRASE / EXACT keyword match types
- [x] MANUAL_CPC with required max CPC and optional daily budget
- [x] MAXIMIZE_CLICKS with required daily target spend and optional max CPC ceiling
- [x] MAXIMIZE_CONVERSIONS with required daily target spend
- [x] canonical geo/language targeting reused from C2
- [x] estimated clicks, cost, average CPC, conversions, and average CPA as returned by the active v25 response
- [x] one-hour best-effort Redis cache keyed by the full forecast request
- [x] UI clearly labels forecast values as estimates rather than guarantees
- [x] selected planner ideas are forecast first; otherwise current campaign Search intents are used
- [x] backend request-shape/date/bid tests and responsive loading/error/result states

Operational behavior:

- [x] `GOOGLE_KEYWORD_PLANNER` and `GOOGLE_FORECAST` fine-grained capabilities are implemented
- [x] managed customer-account validation and ADS_CREATE RBAC
- [x] 6-hour Redis cache for idea responses and 24-hour cache for historical metrics, with best-effort fallback when Redis is unavailable
- [x] activity audit stores counts/network/cache state but not raw user keyword text
- [x] loading, empty, error, cached-result, filtering, selection and mobile horizontal-table states
- [x] backend tests for idea request construction, geo/language resolution, metrics/concept mapping, Redis reuse, historical metrics, and unsupported-radius validation

Google's `GenerateAdGroupThemes` method groups keywords into existing ad groups/campaigns, so it is not used as the pre-campaign grouping primitive. C2 instead exposes current `KEYWORD_CONCEPT` annotations as planning themes. Forecast metrics are implemented as C3 using Google's current planless campaign forecast model. The active v25 `CampaignToForecast` contains language constants, geo constants, a bidding strategy, and forecast ad groups with keyword text/match type; older network/negative-keyword forecast request shapes are not exposed.

Current Reachlyo flow:

~~~
Website/Product + seed terms
   -> keyword ideas
   -> historical metrics
   -> concept/theme review
   -> user selects
   -> canonical Search intents
   -> forecast traffic/conversions
   -> create Search campaign
~~~

Google rate-limits Keyword Planning more tightly than ordinary services and recommends caching/storing results because they are stable over useful periods; historical statistics refresh monthly.

Reference:

- https://developers.google.com/google-ads/api/docs/keyword-planning/overview
- https://developers.google.com/google-ads/api/docs/keyword-planning/generate-keyword-ideas
- https://developers.google.com/google-ads/api/docs/keyword-planning/generate-historical-metrics
- https://developers.google.com/google-ads/api/docs/keyword-planning/generate-forecast-metrics
- https://developers.google.com/google-ads/api/reference/rpc/v25/KeywordPlanIdeaService

## G-06 Google assets/extensions - P1

Status: **complete for C4 — reusable extension, image, and business-identity asset management implemented**.

Implemented reusable Google asset library:

- [x] advertiser-created Sitelinks
- [x] Callouts
- [x] Structured Snippets
- [x] Call assets
- [x] Price assets with 3–8 offerings, language, type, qualifier, currency/unit and final URLs
- [x] Promotion assets with percentage or money-off discounts, modifier, code, occasion/language and final URL
- [x] Lead Form assets with business/CTA/form copy, privacy policy, standard contact fields and post-submit configuration
- [x] provider read-back filters out automatically-created assets so Google text-customization output is not presented as an advertiser-managed reusable asset

Association layer:

- [x] CustomerAsset attach/detach for supported reusable extensions
- [x] CampaignAsset attach/detach
- [x] AdGroupAsset attach/detach
- [x] idempotent association checks before create
- [x] detach removes the exact provider resource returned by Google rather than reconstructing composite resource names
- [x] Lead Form association is hard-limited to CampaignAsset, matching Google's current public API support
- [x] local campaign/ad-group ownership/account/access checks before provider mutation
- [x] activity audit for create/attach/detach

Upgraded URL controls:

- [x] customer tracking template + final URL suffix using the dedicated Google Customer mutate endpoint
- [x] campaign tracking template + final URL suffix + custom parameters
- [x] ad-group tracking template + final URL suffix + custom parameters
- [x] Sitelink asset-level final URL/mobile URL/tracking template/final URL suffix/custom parameters
- [x] maximum eight custom parameters with provider-compatible key/value validation

UI:

- [x] one reusable manager on native Google campaign and ad-group detail pages
- [x] Account defaults / Campaign / Ad-group scope tabs
- [x] create and reuse assets instead of cloning one asset per campaign
- [x] attach/detach state, loading/error/empty states and responsive layout
- [x] advanced Call/Price/Promotion/Lead Form creation
- [x] URL option inheritance editing at the selected scope

C4 visual/identity completion:

- [x] campaign/ad-group `AD_IMAGE` reusable image assets with validated image upload
- [x] Business Name identity assets backed by raw `TEXT` assets and linked with `BUSINESS_NAME`
- [x] Business Logo identity assets backed by raw `IMAGE` assets and linked with `BUSINESS_LOGO`
- [x] role-aware attach/detach so raw TEXT/IMAGE assets retain the correct semantic link type after refresh
- [x] scope eligibility UX and backend enforcement: Lead Form campaign-only, Business Name customer/campaign, Business Logo campaign-only, AD_IMAGE campaign/ad-group
- [x] reusable visual asset read-back includes text/image URL and dimensions
- [x] backend regression coverage for raw IMAGE -> AD_IMAGE association semantics

Google remains authoritative for account-specific policy and serving eligibility; Reachlyo blocks structurally invalid linkage scopes before mutation and surfaces provider errors for account-specific restrictions.

Google's current asset model creates an `Asset` once and associates it through `CustomerAsset`, `CampaignAsset`, or `AdGroupAsset` where that asset field type is supported. Lead Forms are campaign-only. Automatically-created assets are deliberately excluded from the reusable management surface because Google marks them as system-generated/non-editable.

Reference:

- https://developers.google.com/google-ads/api/docs/assets/overview
- https://developers.google.com/google-ads/api/samples/add-lead-form-asset
- https://developers.google.com/google-ads/api/docs/ads/upgraded-urls/supported-entities
- https://developers.google.com/google-ads/api/fields/v25/asset

## G-07 Google AI asset generation - P1

Status: **complete for C6 — review-first Google AssetGenerationService integration implemented and closed-beta gated**.

Implemented:

- [x] current v25 `AssetGenerationService.GenerateText` integration
- [x] HEADLINE / LONG_HEADLINE / DESCRIPTION generation
- [x] landing-page URL, freeform prompt, and keyword generation context
- [x] current v25 `AssetGenerationService.GenerateImages` integration
- [x] MARKETING_IMAGE / SQUARE_MARKETING_IMAGE / PORTRAIT_MARKETING_IMAGE / TALL_PORTRAIT_MARKETING_IMAGE outputs
- [x] final-URL and freeform image generation modes
- [x] SEARCH / PERFORMANCE_MAX / DISPLAY / DEMAND_GEN channel validation
- [x] account capability changed from PLANNED to ACCOUNT_ELIGIBILITY_REQUIRED because Google documents AssetGenerationService as closed beta
- [x] account-managed/RBAC validation before provider calls
- [x] user-visible eligibility/unavailable state
- [x] editable/copyable generated text drafts
- [x] temporary generated image previews with explicit provider-URL warning
- [x] generation audit events for text/images without logging prompt output as credentials or silently publishing content
- [x] backend tests for payload mapping, response mapping, and unavailable-account blocking
- [x] campaign-detail AI asset studio for eligible Google channel types
- [x] generated suggestions are never automatically attached, published, or activated

Google remains authoritative for closed-beta enrollment. A capability may be structurally implemented in UnifyDesk while the selected Google Ads account is still rejected by the provider as ineligible.

Reference:

- Google Ads API v25 AssetGenerationService:
  https://developers.google.com/google-ads/api/reference/rpc/v25/AssetGenerationService
- GenerateText:
  https://developers.google.com/google-ads/api/reference/rpc/v25/AssetGenerationService/GenerateText
- GenerateImagesRequest:
  https://developers.google.com/google-ads/api/reference/rpc/v25/GenerateImagesRequest
- v25 release notes:
  https://developers.google.com/google-ads/api/docs/release-notes

## G-08 Performance Max completeness - P1

Status: **in progress — C5 URL/automation, Brand Guidelines, and lifecycle/customer-acquisition controls are complete; deeper diagnostics/performance and listing-group work remain**.

Implemented in C5:

- [x] existing campaign URL options remain available through the shared Google URL-options manager
- [x] Final URL Expansion control using current campaign `asset_automation_settings`
- [x] independent text asset automation with the provider rule that it cannot be disabled while Final URL Expansion is enabled
- [x] generated enhanced YouTube video opt-in/out
- [x] generated image enhancement opt-in/out
- [x] v25 automated video crawl opt-in/out with explicitly approved LANDING_PAGE / SOCIAL / YOUTUBE sources
- [x] Final URL Expansion URL exclusions synchronized as negative WEBPAGE campaign criteria
- [x] v25.2 Asset Group tracking template, final URL suffix, and custom URL parameters
- [x] read/edit campaign UI with audit logging and RBAC
- [x] read/edit Asset Group URL-options UI
- [x] regression coverage for automation dependency and provider mutation payload

Broader PMax work after C5:

Implemented in the Ads completion branch:

- [x] Brand Guidelines are enabled by default in UnifyDesk's new standard Performance Max product flows
- [x] `BUSINESS_NAME`, `LOGO`, and optional `LANDSCAPE_LOGO` are linked as campaign assets when Brand Guidelines are enabled
- [x] brand main/accent colors with paired-color validation
- [x] Google-supported predefined brand fonts
- [x] provider read-back of Brand Guidelines and campaign-level brand asset serving status/reasons
- [x] migration of older eligible Performance Max campaigns using `EnablePMaxBrandGuidelines` with Google's top-performing brand-asset auto-population
- [x] immutable enablement is handled safely: enabled Brand Guidelines cannot be disabled
- [x] unified campaign wizard, native creation UI, native campaign editor, RBAC/audit flow, launch preflight, plan review metadata, and regression coverage
- [x] Google v25 account lifecycle goals through the current `Goal` resource for New Customer Acquisition, Customer Retention, and Loyalty Retention
- [x] Google `UserListCustomerType` classification management with provider conflict validation
- [x] Performance Max campaign-specific `CampaignGoalConfig` read/create/update/remove
- [x] New-customer `TARGET_ALL` / `TARGET_SPECIFIC` modes with Google value-override constraints
- [x] retention campaign controls with explicit allowlist warning for `TARGET_SPECIFIC`
- [x] loyalty value multiplier, loyalty-member bid adjustments, and eligible PLA benefit controls
- [x] lifecycle readiness validation in unified preflight so launches cannot silently create account-wide lifecycle state
- [x] unified Performance Max launch operation `configure-pmax-lifecycle`
- [x] native Performance Max lifecycle editor, RBAC/audit flow, provider read-back, and regression coverage

Still add/review:

- value multipliers beyond the currently productized lifecycle subset when Google makes them broadly supported/stable for the relevant PMax goal types
- deeper asset strength/diagnostics
- asset group performance
- listing groups where relevant
- any remaining URL-inclusion behavior that becomes directly writable for this PMax subtype

Google v25.2 adds AssetGroup URL options and automated video crawl settings.

Reference:

- https://developers.google.com/google-ads/api/performance-max/getting-started
- https://developers.google.com/google-ads/api/performance-max/create-campaign
- https://developers.google.com/google-ads/api/docs/release-notes

## G-09 Demand Gen completeness - P1

Status: **complete for C7 — current API-supported Demand Gen targeting/control coverage implemented**.

Implemented in C7:

- [x] Demand Gen ad-group channel controls using current `demand_gen_ad_group_settings.channel_controls`
- [x] `ALL_CHANNELS` strategy
- [x] `ALL_OWNED_AND_OPERATED_CHANNELS` strategy
- [x] individual YouTube In-Feed / In-Stream / Shorts, Discover, Gmail, Display, and Maps controls
- [x] new Demand Gen ad groups default to `ALL_CHANNELS`
- [x] new Demand Gen ad groups are created with immutable `audience_setting.use_audience_grouped = true`
- [x] legacy ad-group remediation when grouped Audience targeting was not enabled at creation
- [x] ad-group country / region / city / postal-code location targeting
- [x] negative location exclusions
- [x] ad-group language targeting by language code or name
- [x] reusable Google `Audience` resource discovery and positive Audience criteria
- [x] provider read-back of channel controls, location/language criteria and Audience criteria
- [x] preservation of provider-side geo/language resource targets until the user explicitly removes them
- [x] Demand Gen total-budget creation using `CampaignBudget.period = CUSTOM_PERIOD` and `total_amount_micros`
- [x] total-budget validation requiring campaign start and end dates
- [x] existing unified Google conversion-goal mapping from B6 remains the Demand Gen conversion-goal selection path
- [x] 30-day channel-performance drill-down using `segments.ad_network_type` with impressions, clicks, spend, conversions and conversion value
- [x] responsive ad-group targeting/channel UI, RBAC, audit logging, loading/empty/error states
- [x] backend regression coverage for channel controls, immutable Audience behavior, creation defaults and total-budget payloads

Provider boundaries intentionally respected:

- Demand Gen does not expose Search-style network toggles; current channel controls are used instead.
- Device / OS controls are not exposed as invented standalone C7 settings when the current Demand Gen API does not document an equivalent supported mutation surface.
- Google Audience criteria are positive-only; audience-level exclusions belong inside the reusable `Audience` dimensions rather than a negative Audience criterion.
- C7 location editing supports Google geo targets (country/region/city/postal). Proximity/radius targeting is not exposed in this Demand Gen editor because it is not part of the verified C7 flow.
- Existing creative support (Video, Multi-Asset, Carousel) remains unchanged and is reused by this targeting layer.

Reference:

- https://developers.google.com/google-ads/api/docs/campaigns/demand-gen/overview
- https://developers.google.com/google-ads/api/docs/campaigns/demand-gen/channel-controls
- https://developers.google.com/google-ads/api/docs/campaigns/demand-gen/audiences
- https://developers.google.com/google-ads/api/docs/campaigns/demand-gen/budgets

## G-10 App campaigns - P1/P2

Status: **complete for C8 — current Google Ads API App campaign creation, management, creative and reporting flows implemented**.

Implemented in C8:

- [x] Google capability catalog marks `GOOGLE_APP_CAMPAIGN` implemented with managed-account/API access requirements
- [x] App campaign sync normalization: Google `MULTI_CHANNEL` plus `APP_CAMPAIGN*` subtype persists as UnifyDesk objective `APP`, while exact subtype is preserved
- [x] standard `APP_CAMPAIGN` creation for installs, in-app actions and in-app conversion value
- [x] `APP_CAMPAIGN_FOR_ENGAGEMENT` creation for existing app users
- [x] Android `APP_CAMPAIGN_FOR_PRE_REGISTRATION` creation
- [x] Google Play package ID and Apple App Store ID validation
- [x] current bidding-goal matrix:
  - target install cost
  - maximize installs without target install cost
  - in-app conversions with target install cost
  - in-app conversions with target conversion cost
  - maximize in-app conversions without target CPA
  - target ROAS
  - maximize total conversion value without target ROAS
  - pre-registration conversion volume
- [x] target CPA / Maximize Conversions / Target ROAS / Maximize Conversion Value campaign mutation payloads mapped to the selected App optimization goal
- [x] selective optimization with synchronized App-origin Google conversion actions
- [x] Firebase SDK conversion enforcement for tROAS and maximize-conversion-value App goals
- [x] pre-registration `APP_PRE_REGISTRATION` optimization goal and provider-managed conversion behavior
- [x] campaign-level location targets, location exclusions and languages
- [x] non-shared App campaign budgets
- [x] paused-first campaign creation
- [x] App-specific ad groups without an explicit ad-group type
- [x] App engagement user-list targeting and optional ad-group target CPA
- [x] generic ad-group creation/CPC mutation guards so callers cannot bypass App-specific rules
- [x] subtype-aware App ads:
  - `APP_AD`
  - `APP_ENGAGEMENT_AD`
  - `APP_PRE_REGISTRATION_AD`
- [x] inline headline/description assets plus reusable advertiser IMAGE and YOUTUBE_VIDEO assets
- [x] App creative asset discovery UI with image preview / YouTube identifiers
- [x] App campaign creation modal, App ad-group modal, App ad builder and App-specific campaign management panel
- [x] provider read-back for App settings, subtype, optimization goal and selective conversion actions
- [x] App asset creative sync/persistence after provider refresh
- [x] 30-day campaign metrics including impressions, clicks, spend, conversions and all conversions
- [x] iOS SKAdNetwork install/total-conversion metrics
- [x] App creative asset performance from `ad_group_ad_asset_view`
- [x] RBAC, activity audit and paused-first mutation behavior
- [x] regression coverage for campaign payloads, Firebase value goals, pre-registration restrictions, engagement user lists, subtype-specific ad formats, capability status and sync normalization

Provider boundaries intentionally respected:

- **App Campaign Legacy is not created by the Google Ads API**; it remains a Google Ads UI-only legacy flow.
- App optimization bidding is goal-driven. UnifyDesk does not expose Search-style generic bid-strategy mutation on App campaigns.
- Standard App ad groups do not expose manual CPC bidding.
- App engagement user-list requirements and ad-group target CPA behavior are handled only in the dedicated engagement flow.
- Pre-registration is exposed for Google Play and uses Google-managed pre-registration conversion behavior.
- Images and YouTube videos are reused as Google Ads `Asset` resources; C8 does not duplicate media into every ad.
- Google may generate additional App assets from store content according to provider behavior; UnifyDesk does not represent generated provider content as user-supplied assets.

References:

- https://developers.google.com/google-ads/api/docs/app-campaigns/overview
- https://developers.google.com/google-ads/api/docs/app-campaigns/create-campaign
- https://developers.google.com/google-ads/api/docs/app-campaigns/create-ad-group
- https://developers.google.com/google-ads/api/docs/app-campaigns/create-ad
- https://developers.google.com/google-ads/api/docs/app-campaigns/reporting

## G-11 Portfolio/shared bidding - P2

Research and implement where supported:

- portfolio bid strategies
- shared budgets
- assignment
- reporting
- compatibility validation by campaign type

Do not expose portfolio options to campaign types that do not support them.

## G-12 Experiments - P2

Status: **complete for E1's verified Google v25 product scope**.

Implemented:

- [x] `GOOGLE_EXPERIMENTS` capability AVAILABLE for managed Google accounts
- [x] provider-native experiment and arm read-through, including draft visibility
- [x] direct experiment metrics with control metrics and Google statistical fields
- [x] system-managed `SEARCH_CUSTOM` creation from a synchronized Search campaign
- [x] exactly one control arm plus one treatment arm with configurable integer traffic split totaling 100%
- [x] Google-created treatment draft campaign is mutated before scheduling
- [x] current UnifyDesk Search treatment supports deliberate bidding-strategy variation
- [x] `COMPARE_CAMPAIGNS` campaign-mix creation using existing campaigns
- [x] campaign-mix account/allowlist failures are surfaced rather than replaced with another experiment type
- [x] SETUP-first workflow; no experiment is scheduled automatically
- [x] schedule, end, promote and graduate lifecycle actions where the provider permits them
- [x] graduation creates a new non-shared budget mapping for the selected treatment campaign
- [x] long-running operation/read-back visibility
- [x] ADS_READ / ADS_CREATE / ADS_MODIFY RBAC
- [x] activity audit for create/schedule/end/promote/graduate
- [x] setup failure cleanup attempts to remove the non-serving experiment
- [x] Analytics UI with creation, arms, metrics/statistical fields and lifecycle actions
- [x] backend capability/lifecycle regression coverage

Provider boundary:

- Google v25 has four distinct experiment workflows: system-managed, intra-campaign, asset optimization and campaign mix.
- UnifyDesk creates the two workflows for which it has a safe general-purpose UX today: Search custom and campaign mix.
- Provider-created intra-campaign, asset-optimization and other specialized experiment types are still discovered and reported through the shared read model. Their setup remains provider-hosted until UnifyDesk has a type-specific UX that can collect every required provider field instead of fabricating a generic payload.
- Campaign mix is allowlist/account dependent.

References:

- https://developers.google.com/google-ads/api/docs/experiments/overview
- https://developers.google.com/google-ads/api/docs/experiments/system-managed
- https://developers.google.com/google-ads/api/docs/experiments/intra-campaign
- https://developers.google.com/google-ads/api/docs/experiments/campaign-mix
- https://developers.google.com/google-ads/api/docs/experiments/reporting
- https://developers.google.com/google-ads/api/reference/rpc/v25/GraduateExperimentRequest

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

Status: **substantially implemented for C9 — provider-specific drill-down now complements the normalized analytics layer**.

Implemented before C9:

- [x] search-term reporting
- [x] keyword performance in normalized entity reporting
- [x] reusable asset reporting
- [x] Performance Max asset-group / asset reporting
- [x] AI Max match-source and combination reporting
- [x] normalized Google + Meta daily/entity analytics
- [x] normalized demographic, geography, delivery, device and conversion/action breakdowns

Added in C9:

- [x] Google landing-page performance via `landing_page_view`
- [x] Google actual-delivery placement reporting via `group_placement_view`
- [x] placement type, network, target URL, campaign/ad-group context and conversion metrics
- [x] Google Search Auction Insights by competing domain
- [x] impression share, overlap, position-above, top/absolute-top and outranking metrics
- [x] Google conversion-attribution drill-down by conversion action, category and external conversion source
- [x] conversions/all-conversions and corresponding value metrics
- [x] Google Change Event reporting with actor, client, resource type, operation and changed fields
- [x] 30-day Change Event API boundary enforced while the main provider report supports up to 90-day performance ranges
- [x] optional campaign filtering for all provider-reporting surfaces
- [x] server-side ordering and row caps for large accounts
- [x] dedicated Google provider drill-down UI inside the existing Analytics workspace
- [x] RBAC through the existing ADS_READ reporting route
- [x] backend regression coverage for range validation, campaign filters and provider-native row mapping

Intentionally deferred to their dedicated roadmap slices rather than fabricated inside C9:

- experiment performance depends on E1 Google experiments
- recommendation-impact measurement builds on B9/native recommendation lifecycle work
- lifecycle/customer-acquisition reporting requires explicit provider/customer-acquisition implementation rather than generic conversion guesses

Keep the normalized reporting layer for cross-provider comparison, and use the provider drill-down for provider-native concepts that do not map cleanly across Google and Meta.

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

Status: **partially implemented**. The business-experience pass adds provider-backed Meta interest search/selection and carries verified interest IDs through the unified paused-launch ad-set targeting payload. Canonical free-text interests are no longer treated as provider-executable unless mapped to real Meta targeting IDs.

Still add/research support for:

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

Status: **core geo targeting complete**. Reachlyo now supports provider-valid Meta regions, cities and postal targets, coordinate radius targeting, home/recent location types, provider geo search, synced targeting reconstruction in the native editor, and canonical Reachlyo location resolution during launch preflight.

Implemented:

- [x] regions
- [x] cities
- [x] ZIP/postal where supported
- [x] radius targeting
- [x] location types
- [x] provider search UI for valid geo IDs
- [x] canonical Reachlyo location -> provider mapping during preflight/launch

Geo exclusions are intentionally not part of B3 and can be added with the broader targeting/exclusion work.

## M-03 Placement tree - P0

Status: **core placement tree complete**. B4 carries manual Facebook, Instagram, Messenger, and Audience Network placement positions through native Meta create/edit/read-back and the unified Reachlyo campaign launch path. Automatic placement mode remains supported by omitting manual publisher-platform constraints. A dedicated META_PLACEMENT_TREE capability is exposed, and the compiler surfaces placement-specific creative-variant/media warnings. B5 adds persisted Feed/Story/Reel image/video Creative Variants and Meta placement asset customization for mixed fully modeled placement families. Provider/account-specific Advantage+ eligibility remains in D3 rather than being falsely treated as generic placement support.

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

Status: **complete for D2 — Meta v26 Advantage+ audience controls implemented across native and unified campaign flows**.

Implemented:

- [x] current Meta v26 `targeting_automation.advantage_audience` support
- [x] `targeting_automation.individual_setting.age` support
- [x] `targeting_automation.individual_setting.gender` support
- [x] explicit opt-in; existing/native API callers are not silently broadened
- [x] native Meta ad-set create UI
- [x] native Meta ad-set edit/read-back from synchronized `targeting`
- [x] unified campaign provider-settings UI and persisted provider overrides
- [x] unified Meta compiler requires `META_ADVANTAGE_AUDIENCE` capability when enabled
- [x] unified launch preflight rejects age/gender suggestions unless Advantage+ audience is enabled
- [x] unified launch executor carries the same Advantage+ flags into paused ad-set creation
- [x] provider capability changed from PLANNED to AVAILABLE
- [x] create/update RBAC remains ADS_CREATE / ADS_MODIFY
- [x] activity audit records Advantage+ state changes without duplicating full provider payloads
- [x] regression coverage for native payload shape, invalid suggestion combinations, capability state, and unified compiler behavior

UX/provider semantics:

- location targeting and explicit excluded Custom Audiences are presented as controls
- included audiences remain audience inputs that Meta may expand beyond when Advantage+ audience is enabled
- age and gender are explicitly shown as suggestions only when their corresponding `individual_setting` switches are enabled
- UnifyDesk does not claim Meta will honor a suggestion as a strict constraint
- provider/account/objective eligibility remains authoritative; Meta errors are surfaced if a selected configuration is unavailable for that ad account or campaign context

Verified against the active project Meta Graph API v26.0 model and current Meta Business SDK targeting fields.

## M-05 Advantage+ placements - P1

Status: **complete for D3 — Advantage+ placements is now an explicit, capability-gated Meta placement mode while preserving B4/B5 manual placement and creative-variant behavior**.

Implemented:

- [x] explicit Advantage+ placements selection in the unified Meta provider settings
- [x] provider override persists `advantagePlacements=true` instead of inferring product intent only from empty arrays
- [x] backward-compatible read-back: older campaigns with no manual publisher platforms still hydrate as automatic placements
- [x] unified compiler requires `META_ADVANTAGE_PLACEMENTS` when the explicit automatic mode is selected
- [x] compiler rejects conflicting automatic + manual publisher-platform/position settings
- [x] explicit manual mode requires at least one publisher platform
- [x] native create/edit/read-back continues to represent automatic placement correctly by omitting manual `publisher_platforms` and position restrictions
- [x] existing B4 placement tree remains available for manual Facebook, Instagram, Messenger and Audience Network selection
- [x] existing B5 Feed/Story/Reel placement-specific creative mapping remains intact for manual placement selection
- [x] activity audit records whether an ad-set mutation is using automatic/Advantage+ or manual placement selection without logging provider tokens/payload secrets
- [x] capability and compiler regression coverage
- [x] responsive unified/native placement UI wording distinguishes Advantage+ placements from manual placement restrictions

Provider semantics:

- UnifyDesk does not invent a separate Graph API `advantage_placements` targeting field.
- Advantage+ placements is represented by not constraining the ad set with manual publisher-platform/position targeting; Meta remains authoritative for the eligible inventory available to the account/objective/creative.
- Manual placement selection is mutually exclusive with the explicit Advantage+ placements mode.
- Existing account/objective/creative eligibility and provider-side validation still apply.

## M-06 Advantage+ creative - P1

Status: **complete for D4 — Meta standard creative enhancements can be explicitly opted into from native and unified creative flows without silently enabling later generative features**.

Implemented:

- [x] `META_ADVANTAGE_CREATIVE` capability moved from PLANNED to AVAILABLE
- [x] explicit native Meta creative opt-in
- [x] explicit unified campaign provider setting persisted as `advantagePlusCreative`
- [x] compiler capability gating and review warning
- [x] unified launch carries the opt-in through both standard and placement-customized creative creation
- [x] provider payload uses `degrees_of_freedom_spec.creative_features_spec.standard_enhancements.enroll_status`
- [x] explicit opt-in sends `OPT_IN`; explicit opt-out can send `OPT_OUT`; unspecified legacy callers remain unchanged
- [x] provider read-back requests `degrees_of_freedom_spec` and exposes normalized Advantage+ creative state
- [x] activity audit records Advantage+ creative state without logging provider tokens
- [x] regression coverage for provider payload, compiler requirements and capability availability
- [x] existing placement-specific asset customization remains compatible

Scope boundary:

- D4 covers the generic standard-enhancements enrollment only.
- Text generation remains D5.
- Image uncrop/expansion remains D6.
- Background generation remains D7.
- UnifyDesk does not treat those generative features as implicitly enabled by D4.

Official Meta references used for the broader creative automation roadmap:

- https://www.postman.com/meta/facebook-marketing-api/collection/r3touuy/mapi-generative-ai-features
- Text generation:
  https://www.postman.com/meta/facebook-marketing-api/folder/wd8whru/text-generation
- Background generation:
  https://www.postman.com/meta/facebook-marketing-api/folder/i5cscav/background-generation
- Image expansion examples:
  https://www.postman.com/meta/facebook-marketing-api/request/f4q2498/8-creating-single-image-ad-with-image-uncrop

### D5 implementation note — Meta generative text

Status: **complete for the current Meta v26 generative-text scope**.

Implemented:

- [x] `META_GENERATIVE_TEXT` capability moved from PLANNED to AVAILABLE
- [x] explicit native creative opt-in
- [x] explicit unified campaign opt-in persisted as `generativeText`
- [x] Marketing API payload uses `degrees_of_freedom_spec.creative_features_spec.text_generation.enroll_status`
- [x] explicit opt-in/opt-out semantics without silently enabling the feature for legacy callers
- [x] unified compiler requires the feature capability when selected
- [x] compiler blocks obviously insufficient source copy before remote mutation
- [x] provider remains authoritative for account/creative eligibility and returns the final validation result
- [x] launch executor carries text-generation state into standard and placement-customized creatives
- [x] provider creative read-back normalizes text-generation enrollment
- [x] audit metadata records text-generation state without storing tokens or generated customer data
- [x] tests cover provider payload, capability state, compiler review warning and short-copy rejection

Safety/product semantics:

- generated text is treated as provider-generated creative output, not as user-authored copy
- UnifyDesk surfaces review/preview guidance before activation rather than claiming generated variants are automatically approved
- catalog and other provider-ineligible combinations are left to current Meta validation instead of being faked locally
- D6 image expansion and D7 background generation remain separate opt-ins

Verified against Meta's current Marketing API generative-AI examples, including standalone creative text generation and preview/read-back through `degrees_of_freedom_spec`.

### D7 implementation note — Meta background generation

Status: **complete for the current public Meta catalog/background-generation contract**.

Implemented:

- [x] `META_BACKGROUND_GENERATION` modeled as account/product eligibility required
- [x] separate catalog-ad and background-generation controls in the unified provider UI
- [x] synchronized product-set selection; arbitrary product-set IDs are not required from users
- [x] compiler requires product-set context and background-generation capability
- [x] launch preflight validates the selected product set belongs to the managed Meta ad account
- [x] catalog ad execution uses Meta's inline creative contract with `product_set_id`
- [x] background generation sends `degrees_of_freedom_spec.creative_features_spec.image_background_gen.enroll_status=OPT_IN`
- [x] ads are created PAUSED
- [x] creation audit records catalog/background-generation state without tokens
- [x] generated-background preview endpoint uses `MOBILE_FEED_STANDARD` with `creative_feature=image_background_gen`
- [x] compiler/capability/catalog-ad payload regression coverage

Provider boundary:

- Meta remains authoritative for exact product eligibility and generated-background availability.
- UnifyDesk does not attempt background generation on standalone non-catalog image creatives.
- Users should review Meta's provider preview before activating an ad.


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

### D9 implementation note — Meta Catalog / product sets

Status: **complete for D9's catalog discovery, product-set selection and paused catalog-ad launch scope**.

Implemented:

- [x] `META_CATALOG` capability AVAILABLE
- [x] business catalog discovery through `/{business-id}/owned_product_catalogs`
- [x] product-set discovery through `/{catalog-id}/product_sets`
- [x] catalogs persisted as `META_PRODUCT_CATALOG` account resources
- [x] product sets persisted as `META_PRODUCT_SET` account resources
- [x] stale/inaccessible catalog resources follow the standard resource-sync lifecycle
- [x] sync response exposes catalog and product-set counts
- [x] unified UI has an explicit Catalog / dynamic product ad mode
- [x] synchronized product-set selector with empty-state guidance
- [x] compiler requires `META_CATALOG` when a product set is selected
- [x] catalog mode requires the Purchase business goal / Meta `OUTCOME_SALES` objective
- [x] launch preflight verifies the selected product set belongs to the managed account
- [x] catalog ad sets include `promoted_object.product_set_id`
- [x] catalog and existing-post creative sources are mutually exclusive
- [x] catalog launch skips unnecessary standalone creative creation
- [x] paused ad creation uses Meta's product-set-aware inline creative contract
- [x] catalog/background creation is audited
- [x] native API surface is available for paused catalog-ad creation
- [x] resource, capability, compiler and catalog-ad payload tests


## M-08 Existing Facebook/Instagram post ads - P1

Status: **complete for D8 — eligible Page posts can be discovered and reused by `object_story_id` without copying post content into UnifyDesk**.

Implemented:

- [x] `META_EXISTING_POST` capability AVAILABLE
- [x] Page post discovery using the existing `pages_read_engagement` grant
- [x] native creative dialog can load eligible posts for the selected Page
- [x] manual object-story ID fallback for provider edge cases
- [x] provider creative creation sends `object_story_id` + Page identity
- [x] provider read-back requests `object_story_id`
- [x] unified campaign provider setting supports existing-post creative source
- [x] compiler capability gating and provider-eligibility warning
- [x] existing-post campaigns do not require Creative Pack media
- [x] unified paused launch creates the reusable existing-post creative before the ad
- [x] catalog and existing-post source modes are mutually exclusive
- [x] ADS_READ for post discovery and ADS_CREATE for creative creation
- [x] activity audit identifies existing-post creative creation without copying post content
- [x] provider payload/compiler/capability regression coverage

Meta remains authoritative for Page/post ownership, publishing state, and ad eligibility.

Reference:

- https://www.postman.com/meta/facebook-marketing-api/request/zakdxi8/createusingexistingcreative


## M-09 Customer-list Custom Audiences - P1

Status: **complete for D1 — Meta v26 customer-list Custom Audience creation and privacy-safe member management implemented**.

Implemented:

- [x] create Meta `CUSTOM` customer-list audiences through the existing audience domain
- [x] explicit `customer_file_source` selection:
  - USER_PROVIDED_ONLY
  - PARTNER_PROVIDED_ONLY
  - BOTH_USER_AND_PARTNER_PROVIDED
- [x] current Meta multi-key member upload contract through `/{custom-audience-id}/users`
- [x] ADD and REMOVE member operations
- [x] backend multi-key schema support for EMAIL, PHONE, FN, LN, FI, CT, ST, ZIP, COUNTRY, GEN, DOBY, DOBM, DOBD and EXTERN_ID
- [x] Meta-compatible normalization and SHA-256 hashing for customer matching identifiers
- [x] EXTERN_ID preserved as advertiser-defined ID per Meta multi-key behavior
- [x] browser-side hashing in the UnifyDesk member UI before sensitive identifiers reach the backend
- [x] backend normalization/hashing retained as a safe API-client fallback
- [x] pre-hashed input validation
- [x] provider batching at 10,000 rows per request
- [x] explicit first-party data / Custom Audience terms confirmation
- [x] provider received/invalid row counts returned without echoing customer identifiers
- [x] customer rows and hashes are not persisted in the UnifyDesk database
- [x] activity logs store only aggregate counts, operation and schema names; no customer identifiers or hashes
- [x] desktop/mobile member management from the existing Audiences page
- [x] sync/read-back remains compatible with existing Meta Custom Audience synchronization
- [x] META_CUSTOM_AUDIENCE_CUSTOMER_LIST capability changed from PLANNED to AVAILABLE
- [x] ADS_CREATE for audience creation and ADS_MODIFY for member add/remove
- [x] regression coverage for creation, hashing, EXTERN_ID behavior, invalid pre-hashed values and capability status

Privacy/provider boundaries:

- Meta remains responsible for matching and eventual audience availability/delivery status.
- UnifyDesk does not infer match rate from submitted row count.
- D1 intentionally does not persist customer-upload jobs containing member data. The lifecycle surfaced to users is provider submission count/status plus the existing synchronized audience delivery/operation status.
- The current UI offers one identifier type per upload for safer manual entry; the backend already supports multi-key rows so a future CSV/contact-segment uploader can use the same API contract.
- Customer data must only be uploaded when the business has appropriate rights, consent/legal basis and has accepted Meta Custom Audience terms.

This later allows UnifyDesk contacts/customers to become first-party advertising audiences without changing the provider contract.

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

Status: **complete for D9's current Meta catalog/product-set advertising scope**.

The implementation now includes synchronized business catalogs/product sets, account-scoped selection, stale-resource handling, capability gating, an OUTCOME_SALES guard, launch preflight, `promoted_object.product_set_id` on the ad set, product-set-aware inline creative creation, PAUSED catalog ads, audit metadata, and the D7 background-generation integration.

References:

- Meta catalog/product-set example:
  https://www.postman.com/meta/facebook-marketing-api/request/0w6p8rh/get-catalog-and-product-set
- Meta background-generation catalog ad:
  https://www.postman.com/meta/facebook-marketing-api/request/5v0d0iw/2-creating-ad-with-image-background-generated-feature


## M-13 WhatsApp Status placement - P1

Status: **resolved for D10 as provider-hosted until Meta publishes a stable public Marketing API placement contract for this integration**.

Implemented:

- [x] `META_WHATSAPP_STATUS` represented as `PROVIDER_HOSTED`, not falsely marked API-available
- [x] capability reason explains the API-documentation/eligibility boundary
- [x] unified provider UI surfaces the limitation
- [x] direct link to Meta Ads Manager for eligible accounts
- [x] UnifyDesk does not invent or send an unverified `whatsapp_status` placement token
- [x] capability regression coverage

Research result:

- Meta has public WhatsApp advertising/product announcements and may expose Status advertising to eligible accounts in provider UI.
- Current official public Marketing API materials reviewed for this slice did not provide a stable placement creation contract that UnifyDesk can safely implement.
- Per the roadmap's provider-safety rule, provider-hosted is the completed product behavior until a verified API contract exists.


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

Status: **core provider mapping complete**. A Reachlyo Campaign selects one business outcome, then stores a provider/account-specific ConversionGoalMapping for that same business goal. Synced Google conversion actions and Meta custom conversions retain their normalized provider category/event for goal-aware recommendations and review warnings. Meta also supports validated standard promoted-object events with a selected Pixel. Google Search preflight re-verifies the selected conversion action, launch creates/reuses a CustomConversionGoal in the effective Google Ads conversion customer, attaches it through ConversionGoalCampaignConfig, and disables other biddable CampaignConversionGoals so optimization stays scoped to the explicitly mapped action. Historical mappings remain scoped to the business goal that created them so changing a campaign goal cannot silently reuse the prior mapping.

A campaign should select a business outcome once.

Map that outcome to provider measurement objects.

Eventually Reachlyo-owned messaging/CRM events should feed provider offline/enhanced conversion systems where lawful and configured. That event-ingestion/offline feedback loop remains future full-funnel attribution work rather than part of B6.

## X-08 Cross-provider reporting - P1

Status: **core unified campaign reporting complete**. Reachlyo Campaign analytics now resolve ProviderDeployments to the normalized provider campaign insight rows and expose one currency-safe parent report with total spend, impressions, clicks, conversions, conversion value, CPA, ROAS, daily trend, provider/channel performance, provider spend share, exact-range availability, and last-sync state. A single refresh operation synchronizes each unique deployed Google/Meta ad account and reports partial provider failures without discarding already stored analytics.

Cross-provider reach is intentionally not summed because Google and Meta users cannot be de-duplicated. Existing provider analytics and breakdown surfaces remain available for provider-specific metrics. Creative- and audience-level cross-provider identity mapping is not fabricated in B7; richer unified creative/audience reporting remains part of C9 once provider ads/assets/audience mappings can be compared on a trustworthy common identity.

Reachlyo campaign reporting includes:

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

Provider-specific metrics should remain accessible.

## X-09 Cross-provider recommendations - P1

Status: **core campaign-level recommendation feed complete**. Reachlyo Campaigns now expose one recommendation surface that combines provider deployment state, the existing provider/account diagnostic lifecycle, seven-day unified campaign analytics, and campaign-targeted Google Ads native recommendations. Recommendations include evidence, severity/category/source, provider/deployment context, and an explicit review path when an existing management surface can resolve the issue.

Current evidence-backed campaign diagnostics include:

- failed or partially failed provider deployment
- stale/missing analytics
- campaign spend with zero recorded conversions
- active provider deployment with no recent spend
- cross-provider CPA divergence only after each compared deployment has at least three conversions
- provider/account blocking diagnostics relevant to a deployment
- campaign-scoped Google native recommendations and their provider impact payload

The recommendation layer does **not** automatically change budgets, bids, targeting, creative, or provider recommendations. It recommends review and routes the user to the relevant management surface. Search-term exclusion remains directly actionable from B8 because that action is explicitly initiated by the user. Automated budget movement/optimization stays in X-10/E4 with approval/guardrail controls.

Google's Recommendation resource is campaign-aware for many recommendation types and exposes provider impact estimates; these are preserved as provider evidence rather than treated as guaranteed outcomes.

Examples for later expansion:

- increase/decrease budget
- move budget between channels
- fix missing creative variant
- replace fatigued creative
- fix conversion tracking
- pause poor deployment
- test a new variation

Automatic changes must require explicit configuration.

## X-10 Automation Rules - P2

Status: **complete for E4's guarded rule-engine scope**.

Implemented:

- [x] persisted company-scoped optimization policies
- [x] recommendation-only, approval-required and automatic modes
- [x] account or campaign scope with managed-account validation
- [x] spend, CPA, ROAS, conversions and CTR conditions
- [x] GT/GTE/LT/LTE/EQ operators
- [x] lookback window, minimum-conversion threshold and cooldown guardrails
- [x] daily budget-movement guardrail validation for advisory budget rules
- [x] recommendation actions for budget, negative-keyword and creative review
- [x] approval workflow and persisted pending state
- [x] automatic execution restricted to reversible campaign PAUSE/ENABLE actions
- [x] hourly scheduled evaluation with configurable cron
- [x] activity/audit metadata for triggered rules
- [x] provider errors are isolated per scheduled policy
- [x] Optimization lab UI for creation, enable/disable, manual evaluation and approval

Safety boundary:

- UnifyDesk does not automatically move provider budgets in E4.
- Budget changes remain recommendation/approval-based because cross-provider money movement cannot be rolled back atomically across Google and Meta.
- Automatic actions are deliberately limited to reversible provider campaign status operations.

## X-11 Unified first-party audience system - P1/P2

Status: **complete for E7's consented provider-audience builder scope**.

Implemented:

- [x] one first-party member input can target multiple managed Google/Meta accounts
- [x] Google Customer Match audience creation + Data Manager upload
- [x] Meta customer-list Custom Audience creation + members upload
- [x] per-provider terms/authorization gates
- [x] Google ad-user-data and ad-personalization consent flags
- [x] email, phone and external-ID input
- [x] provider-specific normalization/hashing delegated to the hardened existing provider services
- [x] per-target partial success/failure result
- [x] no raw member rows persisted by the optimization workspace
- [x] no raw provider exception reflection in privacy-sensitive fan-out responses
- [x] Optimization lab UI with multi-account targets and explicit consent controls

Lifecycle segments such as inactive customers, high-value customers and messaging responders require actual CRM/messaging identities and consent evidence. E7 does not synthesize identity from aggregate ad metrics.

## X-12 Full-funnel attribution - P2

Status: **complete for E8's normalized provider-attributed funnel; deterministic person-level cross-provider attribution is explicitly not claimed**.

Implemented:

- [x] unified campaign funnel from impressions -> clicks -> leads -> conversions -> purchases
- [x] total and per-provider-deployment funnel
- [x] spend and conversion value
- [x] CTR, click-to-lead, click-to-conversion and lead-to-purchase rates
- [x] CPA and ROAS
- [x] same normalized provider-attributed analytics source used by existing campaign reporting
- [x] explicit attribution-mode and limitation fields
- [x] Optimization lab presentation alongside budget evidence

Current attribution mode is `PROVIDER_ATTRIBUTED_NORMALIZED`. Google and Meta identity is not de-duplicated and UnifyDesk does not claim person-level cross-device attribution from provider aggregate data alone.

A later event-level CRM/messaging identity graph can enrich this surface when the user has lawful first-party identity, consent and conversion-event data.

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

## Business-experience implementation note — 2026-09-28

Implemented on `feature/ad-center-business-experience`:

- business-outcome goal cards instead of leading with provider enums
- goal-aware Google/Meta optimization defaults
- rule-based recommended channel selection with user override
- provider settings collapsed behind an Advanced surface
- business-readable review summary before provider plan details
- age presets aligned with Google demographic buckets plus safe custom validation
- raw radius coordinates moved behind an advanced control
- Google Search canonical age/gender execution instead of approximate-only storage
- Meta provider-backed interest search/selection and paused-launch execution

Remaining major product slices:

- provider-backed canonical location autocomplete shared across selected channels
- Google deeper audiences, observation/targeting modes, exclusions and retargeting mappings
- Meta behaviors/additional detailed-targeting categories and exclusions as supported by the active API
- deeper provider-backed audience/exclusion mapping for the unified visual channels
- richer business-level creative generation/preview experience across channels

## Unified Performance Max execution note — 2026-09-28

Implemented on `feature/ad-center-unified-pmax`:

- Performance Max is selectable and executable from the unified campaign wizard
- the shared Creative Pack supports dedicated landscape, square, portrait, square-logo and landscape-logo roles instead of stretching one generic image
- preflight enforces Google Performance Max text counts/limits, business name, final URL and required media roles before provider mutation
- launch uses the existing Google native atomic mutation path to create budget + paused campaign + assets + first paused asset group
- retry uses deterministic launch naming plus Google sync/reuse to avoid duplicate campaigns
- canonical geo/language are applied as campaign targeting controls after creation
- canonical search intent is mapped to Performance Max asset-group search-theme signals
- age/gender/interests are not incorrectly converted into Search-style hard criteria; review explains that the current PMax mapping treats these separately from geo/language targeting
- selected Google conversion mapping is applied to the created Performance Max campaign
- company media is read server-side from configured S3/Supabase storage for Google asset upload without exposing public media URLs
- successful orchestration still stops at `PAUSED_READY`; no automatic spend activation is introduced

## Unified Demand Gen execution note — 2026-09-28

Implemented on `feature/ad-center-unified-demand-gen`:

- Demand Gen is selectable and executable from the unified campaign wizard
- budget + paused Demand Gen campaign + paused grouped-audience ad group are created in one Google bulk mutation to reduce orphaned provider resources
- the shared Google visual Creative Pack roles are reused for Demand Gen landscape/square/portrait images and square logo
- compiler/preflight enforce Demand Gen headline, description, business-name, final-URL and required-media constraints before provider mutation
- canonical country/region/city/postal locations and languages map to Demand Gen ad-group criteria
- proximity/radius locations are blocked for the current unified Demand Gen path rather than being silently dropped
- channel coverage supports all Demand Gen channels or Google-owned channels through provider settings
- the unified path creates a paused Demand Gen multi-asset ad and reuses it by deterministic launch name on retry
- canonical interests/search intent remain approximate unless backed by real Google Audience resources; no provider audience is guessed from free text
- selected Google conversion mapping is applied to the created Demand Gen campaign
- awareness goals now recommend Demand Gen alongside Meta instead of defaulting to Search
- successful launch still stops at `PAUSED_READY`


## Unified Shopping execution note — 2026-09-28

Implemented on `feature/ad-center-unified-shopping`:

- Standard Shopping is selectable and executable from the unified campaign wizard
- a first-class "Sell products from a catalog" business goal recommends Google Shopping alongside Meta
- Shopping-only campaigns skip the Creative Pack because Merchant Center product data supplies titles, images, prices and landing URLs
- provider settings use an accessible Merchant Center account selector rather than raw merchant-ID entry
- campaign priority (0/1/2), local-inventory enablement and EU political-ad declaration are captured explicitly
- preflight verifies Merchant accessibility and requires processed Merchant products before provider mutation
- budget + paused Shopping campaign + paused `SHOPPING_PRODUCT_ADS` ad group + paused product ad + root UNIT listing group are created in one Google bulk mutation
- retry uses deterministic launch naming plus Google campaign sync/reuse to avoid duplicate Shopping campaigns
- canonical geo/language targeting is applied after the atomic Shopping core is created
- demographics, interests and search intent are not silently converted into Shopping product filters
- selected Google conversion mapping is applied to the created Shopping campaign
- successful orchestration still stops at `PAUSED_READY`; no automatic spend activation is introduced


## Google visual-channel provider audiences — 2026-09-28

Implemented on `feature/ad-center-google-visual-audiences`:

- unified provider settings now load synced Google audience/user-list resources for each selected Google Ads account
- only accessible Google lists marked provider-eligible for Display can be selected
- selections are isolated per visual channel and are cleared when that channel changes Google Ads accounts
- Google Display maps included lists to positive ad-group `USER_LIST` criteria and exclusions to negative `USER_LIST` criteria
- Display ad groups explicitly use AUDIENCE Targeting mode rather than Observation so selected lists narrow delivery
- Google Demand Gen composes selected lists and exclusions into a deterministic reusable CUSTOMER-scoped Google Audience, then applies it as grouped audience targeting
- reusable Demand Gen audience names include a stable selection fingerprint so retries reuse the same composition while changed selections do not accidentally reuse stale provider state
- Performance Max maps selected lists to asset-group audience signals; the UI does not present PMax audience exclusions because signals do not hard-restrict delivery
- PMax retry handling avoids creating a duplicate audience signal when the asset group already has one
- compiler plans now schedule Display targeting and PMax audience-signal steps even when provider-backed lists are the only audience input and no canonical audience definition exists
- canonical free-text interests/demographics remain separate from provider-backed Google lists and are not silently converted into unsupported hard targeting
- all launch paths remain paused-first and still stop at `PAUSED_READY`

## Provider-backed canonical location autocomplete — 2026-09-28

Implemented on `feature/ad-center-location-autocomplete`:

- the unified audience step can search provider-backed country/region/city/postal targets instead of relying only on manually typed geo names
- Google suggestions are resolved through the managed Google Ads account using GeoTargetConstant suggestions
- Meta suggestions reuse the existing account-authenticated geo targeting search
- provider-specific terms are normalized to one canonical `COUNTRY / REGION / CITY / ZIP` vocabulary
- identical Google and Meta places merge into one canonical suggestion with provider-support badges
- when multiple providers are selected, suggestions unsupported by every selected provider remain visible but cannot be selected
- selecting a suggestion fills the existing canonical audience fields, so no database migration or provider-specific audience storage is introduced
- radius targeting remains an explicit coordinate-based advanced option and is not guessed from autocomplete results
- autocomplete failures are surfaced before campaign compilation rather than allowing false provider compatibility

## Unified Display execution note — 2026-09-28

Implemented on `feature/ad-center-unified-display`:

- Google Display is selectable and executable from the unified campaign wizard
- budget + paused Display campaign + paused `DISPLAY_STANDARD` ad group are created in one Google bulk mutation
- the shared visual Creative Pack supplies landscape marketing image, square marketing image and optional square logo
- compiler/preflight enforce Display headline, long-headline, description, business-name, destination URL and required visual roles before provider mutation
- Display preflight reads private company media server-side and validates image type, size, minimum dimensions and aspect ratio before Google asset creation
- responsive Display image assets + paused responsive display ad are created in one Google bulk mutation to reduce orphaned assets on partial provider failure
- Manual CPC, Maximize Conversions and Target CPA are supported through business-readable provider settings
- Manual CPC can use an explicit CPC bid or a conservative derived fallback from the daily budget
- canonical geo/language are mapped at campaign level
- demographics, interests, retargeting and customer segments are not silently converted into incorrect campaign criteria; review reports current Display audience mapping as partial
- selected Google conversion mapping is applied when configured
- deterministic campaign/ad-group/ad naming plus provider sync/reuse protects launch retries from duplicate core resources
- successful orchestration still stops at `PAUSED_READY`; no automatic spend activation is introduced


## Unified Display execution note — 2026-09-28

Implemented on `feature/ad-center-unified-display`:

- Standard Display is selectable and executable from the unified campaign wizard
- the shared Google visual Creative Pack is reused for responsive display assets instead of adding a separate upload model
- compiler/preflight enforce Display headline, description, long-headline, business-name, destination-URL and required landscape/square-image constraints before provider mutation
- budget + paused Display campaign + paused Display ad group are created atomically in one Google bulk mutation
- the responsive display ad is created as a separate paused, retry-safe saga step from the shared Creative Pack
- optional square logo is reused when available; landscape and square marketing images are required and image bytes/dimensions/aspect ratios are validated
- Traffic/Awareness default to Manual CPC; when no advanced CPC is supplied, UnifyDesk derives a conservative ad-group CPC from the daily budget and surfaces that behavior during review
- conversion-oriented goals default to Maximize Conversions, with optional Target CPA available in advanced provider settings
- canonical geo/language are applied to the Display campaign after creation
- demographics, interests, retargeting, search intent and customer segments are not guessed into Display ad-group criteria; review marks that mapping partial until provider-backed audience criteria are available
- selected Google conversion mapping is applied when the business goal requires it
- deterministic campaign/ad-group/ad names plus provider sync/reuse protect retries from duplicate Display resources
- successful orchestration still stops at `PAUSED_READY`; no automatic spend activation is introduced


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

B1. [x] Google geo/language/network/daypart + core demographic targeting — canonical country/region/city/postal/proximity + language mapping, Search Partners/Display expansion, presence mode, quarter-hour dayparting, exact age buckets and gender criteria, provider preflight and idempotent campaign-criterion execution  
B2. [x] Google negative keywords/shared negatives — ad-group negative create/remove + sync, campaign negatives with bulk add/remove, live reusable shared-list discovery/member management/attach-detach, unified campaign selection, preflight validation and idempotent launch execution  
B3. [x] Meta detailed geo targeting — provider geo search, regions/cities/postal codes, coordinate radius targeting, home/recent location types, native create/edit UI, canonical mapping, and launch preflight resolution  
B4. [x] Meta proper placement tree — structured Facebook/Instagram/Messenger/Audience Network positions, native create/edit/read-back, unified launch mapping, capability exposure, and compiler placement warnings  
B5. [x] placement-specific Creative Pack variants — Feed/Story/Reel copy + image/video variants, validated AD_CREATIVE media, single-family launch selection, mixed-placement Meta asset_feed_spec PLACEMENT customization, and safe canonical fallback for unsupported/unmodeled placement families  
B6. [x] unified conversion mapping — one business goal with current-goal provider/account mappings, goal-aware defaults and mismatch warnings, current Meta standard-event validation/custom-conversion execution, and Google conversion-action preflight + CustomConversionGoal + ConversionGoalCampaignConfig + competing-goal isolation  
B7. [x] cross-provider campaign analytics — deployment-linked Google + Meta campaign rollups, unified KPIs, provider/channel spend share, daily trend, exact-range/daily fallback consistency, mixed-currency protection, one-click multi-provider refresh, and partial-sync failure reporting  
B8. [x] search-term reporting — Google search_term_view with matched keyword/match type, targeting status, AI Max match source, impressions/clicks/spend/conversions/value/CPA/ROAS, evidence-backed review filters, promote-to-keyword, and ad-group/campaign exclusion actions  
B9. [x] campaign-level recommendation improvements — unified deployment/provider/cross-channel recommendation feed, failed-delivery and stale-data diagnostics, spend-without-conversion checks, minimum-sample CPA divergence detection, campaign-scoped Google native recommendations, evidence/context/action paths, and responsive campaign UI

## Phase C - Modern Google / P1

C1. [x] AI Max for Search — campaign/ad-group AI Max controls, text and URL automation, text restrictions, retry-safe page-feed inclusions and URL exclusions, verified brand lists, match-source + combination reporting, migration read-back, capability/preflight/audit/UI/tests  
C2. [x] Keyword Planner — v25 idea + historical-metrics service, URL/keyword/site seeds, geo/language/network targeting, competition/bid/monthly metrics, concept themes, Redis caching, selectable Search-intent UI, RBAC/audit/tests  
C3. [x] forecasts — v25 planless keyword forecast API, date horizon, match types, Manual CPC / Maximize Clicks / Maximize Conversions, geo/language reuse, one-hour cache, estimate-only UI, capability/audit/tests  
C4. [x] reusable Google campaign assets/extensions — Sitelink/Callout/Structured Snippet/Call/Price/Promotion/Lead Form plus AD_IMAGE, Business Name and Business Logo; raw TEXT/IMAGE assets use role-aware semantic linkage, correct scope eligibility, URL options, audit/RBAC, responsive UI and regression coverage  
C5. [x] PMax URL/automation updates — current asset automation settings, Final URL Expansion dependency, enhanced video/image controls, approved automated video crawl sources, URL exclusions, v25.2 Asset Group URL options, RBAC/audit/UI/tests  
C6. [x] Google AI asset generation where eligible — v25 closed-beta capability gating, text/image generation from landing page/prompt/keywords, review-first editable drafts, temporary image previews, audit/RBAC/UI/tests, no automatic publishing  
C7. [x] Demand Gen targeting completeness — ad-group geo/language targeting and exclusions, reusable Audience resources with immutable grouped-audience handling, current channel strategy/individual inventory controls, total campaign budget support, channel-performance reporting, RBAC/audit/UI/tests; unsupported device/OS/network controls are not fabricated  
C8. [x] App campaigns — current App installs/in-app action/value, App Engagement and Android Pre-registration campaign creation; validated goal/bidding matrix, App conversion/Firebase rules, geo/language/exclusions, App-specific ad groups/user lists, App/Engagement/Pre-registration creatives with reusable image/video assets, sync normalization, SKAN + asset reporting, capability/RBAC/audit/UI/tests; App Campaign Legacy remains provider-UI-only  
C9. [x] richer provider reporting — Google landing pages, actual placements, Auction Insights, conversion-attribution detail and 30-day change history added as provider-native drill-down inside the normalized Analytics workspace; existing search-term/asset/PMax/AI Max and Meta/native breakdown reporting retained; experiment/recommendation-impact/lifecycle analytics remain tied to their dedicated later slices

## Phase D - Modern Meta / P1

D1. [x] customer-list Custom Audiences — Meta CUSTOM audience creation, customer-file source labeling, browser/backend SHA-256 normalization, add/remove multi-key member batches, explicit terms/authorization, no PII/hash persistence or logging, provider counts/status, RBAC/capability/UI/tests  
D2. [x] Advantage+ audience controls — Meta v26 targeting_automation, age/gender individual suggestion settings, explicit control-vs-suggestion UX, native create/edit/read-back, unified wizard/compiler/launch support, capability/RBAC/audit/tests  
D3. [x] Advantage+ placements — explicit capability-gated automatic-placement mode, backward-compatible read-back, manual-placement conflict validation, native/unified UX, audit and compiler/capability tests  
D4. [x] Advantage+ creative — standard-enhancements opt-in via degrees_of_freedom_spec, native/unified UI, compiler capability gating, launch propagation, provider read-back, audit and tests  
D5. [x] generative text — explicit Meta text_generation opt-in, native/unified controls, compiler eligibility/review validation, launch propagation, provider read-back, audit metadata and tests  
D6. [x] image uncrop/expansion — explicit Meta image_uncrop opt-in, image-asset preflight, native/unified controls, launch propagation, provider read-back, audit metadata and tests  

### D6 implementation note — Meta image expansion

Status: **complete for the current Meta v26 image-uncrop/expansion scope**.

Implemented:

- [x] `META_IMAGE_UNCROP` capability moved from PLANNED to AVAILABLE
- [x] explicit native image-creative opt-in
- [x] explicit unified campaign opt-in persisted as `imageExpansion`
- [x] Marketing API payload uses `degrees_of_freedom_spec.creative_features_spec.image_uncrop.enroll_status`
- [x] explicit opt-in/opt-out while leaving unspecified legacy callers unchanged
- [x] native UI limits the control to single-image creative creation
- [x] unified compiler requires at least one image asset before remote mutation
- [x] compiler requires the image-expansion capability and emits a review warning
- [x] unified launch propagates the preference through standard and placement-customized Meta creative creation
- [x] provider creative read-back normalizes `image_uncrop` enrollment
- [x] activity audit metadata records image-expansion state without provider tokens
- [x] regression coverage for capability availability, provider payload, image preflight and compiler review behavior

Product/provider semantics:

- image expansion is opt-in and Meta remains authoritative for exact placement/creative eligibility
- the original image remains the advertiser-provided source; expanded output should be reviewed through provider preview/read-back before activation
- D6 does not enable background generation
- D7 remains the separate background-generation slice

D7. [x] background generation — eligible catalog/product-set inline ad execution, image_background_gen opt-in, PAUSED launch, audit and provider preview  
D8. [x] existing-post ads — Page post discovery, object_story_id creative reuse, native/unified flow, read-back, audit and tests  
D9. [x] Meta Catalog/product sets — discovery, account-scoped persistence, product-set UI, capability/preflight validation and paused catalog-ad creation  
D10. [x] WhatsApp Status ads where eligible — resolved safely as PROVIDER_HOSTED until a stable public Marketing API placement contract is verified

## Phase E - Optimization / P2

E1. [x] Google experiments — v25 Search custom + campaign mix creation, direct reporting/statistics, schedule/end/promote/graduate, provider-created specialized experiments remain readable/provider-hosted  
E2. [x] Meta experiments — current Business SDK Ad Study / split-test discovery exposed READ_ONLY; low-level ad_studies creation intentionally provider-hosted until a stable customer-facing Marketing API contract is documented  
E3. [x] UnifyDesk experiments abstraction — one cross-provider read model with per-account partial failures while provider-specific mutation semantics remain separate  
E4. [x] automation rules — persisted scheduled rules, recommendation/approval/automatic modes, cooldown/sample guardrails, audit, automatic execution restricted to reversible status actions  
E5. [x] bid/budget simulation — Google campaign_simulation read-through for provider-supported budget/bid/CPA/ROAS/impression-share curves, explicitly labeled historical counterfactual rather than forecast  
E6. [x] cross-provider budget recommendations — advisory normalized Google/Meta allocation using trailing ROAS/conversion efficiency, confidence/data threshold and 10% exploration floor; no automatic money movement  
E7. [x] first-party audience builder — consented multi-provider Google Customer Match + Meta Custom Audience fan-out without raw identifier persistence/log reflection  
E8. [x] full-funnel attribution — normalized provider-attributed impression/click/lead/conversion/purchase funnel with explicit cross-device/person-level limitation  
E9. [x] lifecycle/retargeting automations — persisted scheduled lifecycle policies, aggregate clicked-not-converted and lead-not-purchased signals, first-party audience handoff, and explicit NEEDS_FIRST_PARTY_DATA state for identity-dependent segments

Phase E completion boundary:

- The optimization layer is operational and provider-safe.
- Features that require unavailable/allowlisted/provider-hosted contracts remain explicitly capability-gated instead of being simulated.
- Automatic cross-provider budget movement is not enabled; E6 is advisory and E4 automatic execution is limited to reversible campaign status actions.
- Person-level retargeting membership is never inferred from aggregate advertising metrics.

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
- https://developers.google.com/google-ads/api/fields/v25/search_term_view
- https://developers.google.com/google-ads/api/fields/v25/campaign_search_term_view

Conversion goals:

- https://developers.google.com/google-ads/api/docs/conversions/goals/overview
- https://developers.google.com/google-ads/api/docs/conversions/goals/campaign-goals
- https://developers.google.com/google-ads/api/docs/conversions/getting-started

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

Analytics/reporting:

- https://developers.google.com/google-ads/api/reference/rpc/v25/Metrics
- https://developers.google.com/google-ads/api/fields/v25/campaign

Recommendations:

- https://developers.google.com/google-ads/api/docs/recommendations
- https://developers.google.com/google-ads/api/fields/v25/recommendation

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
- https://www.postman.com/meta/facebook-marketing-api/request/zo3xvu7/get-campaign-insights-l3

Official Meta Business SDK schema/codegen (used to verify asset_feed_spec and placement customization fields):

- https://github.com/facebook/facebook-business-sdk-codegen/tree/main/api_specs/specs
- https://github.com/facebook/facebook-python-business-sdk/blob/main/facebook_business/adobjects/adpromotedobject.py

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
