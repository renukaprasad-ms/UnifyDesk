# Google Ads provider architecture

## Goal

Add Google Ads as the second advertising provider without turning Ad Center into a collection of Meta-specific screens and Google-specific exceptions.

The shared Ad Center owns provider-neutral concepts such as connections, accounts, campaign summaries, analytics, activity, credentials, account selection, and recommendation lifecycle. Provider adapters own OAuth, provider object models, mutations, reporting queries, billing rules, and provider-specific resources.

## Provider boundary

```text
Ad Center
  |
  +-- AdsProviderRegistry
       |
       +-- Meta Ads adapter
       +-- Google Ads adapter
       +-- future adapters
```

`AdsProvider.capabilities()` is the contract exposed to the UI. A provider must advertise only capabilities that are implemented and usable.

Google Ads starts with `ACCOUNT_DISCOVERY`. Later slices add campaign management, creative/assets, conversions, analytics, recommendations, and supported billing capabilities.

## Google connection flow

```text
Browser
  -> GET /api/v1/companies/{companyId}/ads/google/authorize
Backend
  -> stores one-time OAuth state in Redis
  -> returns Google OAuth authorization URL
Browser
  -> Google consent
Google
  -> GET /api/v1/oauth/google-ads/callback?code=...&state=...
Backend
  -> validates and consumes state
  -> exchanges authorization code
  -> fetches stable Google user identity
  -> calls customers:listAccessibleCustomers
  -> expands customer_client hierarchy for manager accounts
  -> encrypts OAuth token set in ad_provider_credentials
  -> persists discovered accounts in ad_accounts
  -> redirects to /ad-center?googleAds=connected
```

The browser never receives access or refresh tokens.

## Authentication and API access

Google Ads OAuth uses:

```text
https://www.googleapis.com/auth/adwords
```

The web flow requests offline access so background sync can refresh access tokens.

As of 2026-09-09 Google Ads API access levels are associated with the Google Cloud project that owns the OAuth credentials. The legacy developer-token header is therefore optional in Reachlyo and retained only for compatibility.

Current integration target: Google Ads API `v25`.

## Account hierarchy

A Google OAuth user may directly access manager and advertiser customers. Reachlyo:

1. calls `customers:listAccessibleCustomers`;
2. reads account metadata for each directly accessible customer;
3. for manager customers, queries `customer_client`;
4. stores the manager used to reach a child account inside provider metadata.

Future Google mutations must use the stored manager customer as `login-customer-id` when required.

## Shared model rules

Do not force Google concepts into Meta terminology.

Meta hierarchy:

```text
Campaign -> Ad Set -> Ad -> Creative
```

Google hierarchy:

```text
Campaign -> Ad Group -> Ads / Keywords / Assets
Campaign -> Campaign Budget
Performance Max -> Asset Group -> Assets
```

Before Google campaign mutations are implemented, the campaign domain will be generalized so the internal middle-level concept is provider-neutral while the UI renders "Ad set" for Meta and "Ad group" for Google.

Provider-only concepts should use provider-specific tables or adapters rather than large nullable shared tables.

## Delivery slices

### Slice 1 - connection and account discovery

- Google provider registration
- server-side OAuth
- Redis state validation
- encrypted refresh-token storage
- token refresh
- direct account discovery
- manager hierarchy discovery
- persistence into shared Ad Center accounts
- frontend Connect/Reconnect support
- shared disconnect support

### Slice 2 - account sync and Search campaigns

- provider account context
- campaign sync through GAQL
- campaign budgets
- ad groups
- keywords
- responsive search ads
- create/update/pause/enable/remove
- activity logging

### Slice 3 - reporting and conversions

- account/campaign/ad-group/ad daily metrics
- conversion actions
- normalized analytics
- provider-specific breakdown mapping
- scheduled incremental synchronization

### Slice 4 - assets and advanced campaign types

Implemented:
- Google account asset library read surface
- Performance Max campaign creation
- atomic campaign budget + campaign + asset + asset group + asset group asset creation
- required Performance Max text/image/business/logo validation
- Performance Max asset group listing and delivery status controls
- Performance Max search-theme signals
- Performance Max Customer Match audience signals
- provider-aware creative UI for reusable Google assets
- Google Smart Bidding support for Maximize Conversions and Maximize Conversion Value
- standard Display campaign creation
- Responsive Display Ad creation with native Google image assets
- Demand Gen campaign creation
- Demand Gen video-responsive ad creation with YouTube + logo assets
- Standard Shopping campaign creation with Merchant Center ID
- initial Shopping Product Ad Group, Product Ad, and root listing group
- synchronization/reporting of existing Video campaigns and ads

Intentional provider limitation:
- Google Ads API exposes Video campaigns for synchronization/reporting but does not support creating or updating Video campaigns. Reachlyo therefore renders Video entities as read-only instead of offering mutations Google cannot honor.

Final advanced slice completed:
- optional Performance Max portrait marketing images and landscape logos
- additional Demand Gen Multi-Asset and Carousel creative formats
- Merchant Center product/data-source management outside the Ads campaign surface

### Slice 5 - recommendations, first-party data, billing, and automation

Implemented:
- native Google RecommendationService read
- native recommendation apply/dismiss actions
- optimization score
- Reachlyo diagnostics and recommendation lifecycle
- Google Customer Match member ingestion through the Data Manager API
- request-level consent and Customer Match terms confirmation
- email/phone normalization + SHA-256 hashing before upload
- Data Manager validate-only mode and field-warning handling
- offline/enhanced conversion event ingestion through Data Manager
- GCLID / GBRAID / WBRAID and hashed first-party matching data
- supported Google billing/account-budget read operations
- scheduled Google account synchronization
- Redis distributed per-account sync locks for multi-pod deployments

Provider-hosted operations:
- payment-method and account-funding actions that Google Ads API does not expose remain in Google Ads

## Future providers

A third provider should require:

1. one `AdsProvider` implementation;
2. provider OAuth/credential adapter;
3. account discovery adapter;
4. capability-specific adapters as features land;
5. provider-specific mapping into shared read models.

No frontend feature should depend on a provider name when capability detection can express the behavior.


## Current Google Ads coverage

```text
Connection / MCC hierarchy            implemented
Search campaigns                      implemented
Performance Max                       implemented
Display                               implemented
Demand Gen                            implemented
Standard Shopping                     implemented
Video                                 sync/report only (Google API limitation)

Campaign budgets                      implemented
Ad Groups                             implemented where channel uses them
Asset Groups                          implemented for Performance Max
Keywords                              implemented for Search only
Responsive Search Ads                 implemented
Responsive Display Ads                implemented
Demand Gen video-responsive ads       implemented
Shopping Product Ads                  implemented
Google asset library                  implemented

Customer Match list creation          implemented
Customer Match member ingestion       implemented via Data Manager API
Conversion actions                    implemented
Offline/enhanced event ingestion      implemented via Data Manager API
Analytics + breakdowns                implemented
Native Google recommendations         implemented
Reachlyo recommendations              implemented
Account health / optimization score   implemented
Billing read                          implemented
Background sync                       implemented
```

Existing Google OAuth connections created before Data Manager or Merchant support must reconnect once so the refresh token includes both:

```text
https://www.googleapis.com/auth/datamanager
https://www.googleapis.com/auth/content
```

Reachlyo never logs raw Customer Match email/phone identifiers. Contact identifiers are normalized and SHA-256 hashed before the Google Data Manager request is made.


## Advanced Google coverage

Implemented beyond core parity:
- add/reuse Google asset library read surface
- Performance Max audience signals and search themes
- Customer Match member upload through Google Data Manager
- offline/enhanced conversion event upload with click IDs and hashed first-party identifiers
- native Google RecommendationService fetch/apply/dismiss actions
- scheduled Google background synchronization
- Display campaign creation and responsive display ads
- Demand Gen campaign creation and YouTube/video responsive ads
- Shopping campaign creation with Merchant Center ID, listing group, and shopping product ads
- standalone Google Video campaign sync/reporting in read-only mode

Google platform limitation:
- standalone Video campaigns cannot be created or updated through the Google Ads API; Google only exposes them for fetch/reporting. Reachlyo therefore treats VIDEO campaigns as read-only and uses Demand Gen for API-managed YouTube/video creation.

Implemented in the final advanced slice:
- add new assets to existing Performance Max Asset Groups
- reuse existing account-level Google assets in Performance Max Asset Groups
- unlink Asset Group assets while enforcing required minimum counts
- optional Performance Max portrait marketing images
- optional Performance Max landscape logos
- optional Performance Max YouTube video assets
- Demand Gen Multi-Asset ads
- Demand Gen Carousel ads with 2–10 card assets
- Merchant API OAuth scope
- accessible Merchant Center account discovery
- Merchant Center API data-source list/create/delete
- processed product catalog listing with pagination
- API product input create/update through idempotent insert/upsert
- API product input deletion
- Merchant product issue visibility
- Shopping campaign Merchant-account discovery instead of manual Merchant ID entry

Merchant API product writes are intentionally limited to API-managed product data sources. Existing file-based and other data sources remain visible but read-only.

