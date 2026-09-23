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

- reusable Google assets
- asset links
- Display / Video / Demand Gen where supported
- Shopping integrations
- Performance Max asset groups

### Slice 5 - recommendations and billing

- Google RecommendationService
- optimization score
- Reachlyo diagnostics
- supported billing setup/read operations
- deep links to Google Ads for payment actions unavailable through API

## Future providers

A third provider should require:

1. one `AdsProvider` implementation;
2. provider OAuth/credential adapter;
3. account discovery adapter;
4. capability-specific adapters as features land;
5. provider-specific mapping into shared read models.

No frontend feature should depend on a provider name when capability detection can express the behavior.
