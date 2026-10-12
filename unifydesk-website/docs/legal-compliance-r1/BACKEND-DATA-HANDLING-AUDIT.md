# UnifyDesk legal-compliance R1 — source-grounded backend audit

Date: 2026-10-12. Baseline: `dev` commit `860bf192912bc972eb9df33c47a5c2cba6b3b206`. Review was GitHub code and technical documentation only; it cannot establish live deployment configuration, signed vendor agreements, successful deletion or production security.

| Data processing | Source | Evidence | Still unknown |
| --- | --- | --- | --- |
| User/company | `company/service/CompanyService.java`, `user/service/UserService.java`, `auth/service/` | User profiles, workspaces, access and deletion-pending operations | Legal operator details, live access enforcement |
| Deletion | `lifecycle/config/DeletionLifecycleProperties.java`, `lifecycle/service/EntityPurgeService.java`, `application.yaml` | Configurable default 30-day delay; background multi-stage purge and retries | Environment overrides, DB-wide purges, backups, external providers, legal holds |
| Consent | `contacts/consent/RecipientEmailUnsubscribeService.java`, `contacts/processing/ContactDeletePurgeWorker.java` | Opt-out update; pseudonymous suppression fingerprint retained during some deletion | All recipient channels, pseudonym storage/key lifetime |
| Inbox | `inbox/entity/InboxMessage.java`, `inbox/service/InboxAttachmentStreamingService.java` | Stored message body, subject, sender, To/CC/BCC; provider or stored attachment streams | Attachment lifecycle and provider-side deletion |
| OAuth and connections | `channel/gmail/GmailOAuthProperties.java`, `channel/microsoft/MicrosoftOAuthProperties.java`, `channel/zoho/ZohoMailOAuthProperties.java`, `channel/entity/ProviderCredential.java` | Gmail gmail.modify, Microsoft Mail.ReadWrite/Mail.Send, ZohoMail.messages.ALL defaults; encrypted credential payload entity | Actual scopes, security assessment, full token revocation |
| Meta/Ads | `channel/meta/MetaWhatsAppConnectionService.java`, `ads/meta/`, `ads/google/` | Connected WABA/account APIs and ads integration code | Meta production approval, app deletion callback, audience sharing |
| Storage | `media/provider/S3StorageProvider.java`, `media/provider/SupabaseStorageProvider.java`, `media/service/MediaCleanupService.java` | Storage provider adapters and pending upload cleanup | Enabled vendor, hosting location, backup lifecycle |
| Notifications | `notification/platform/core/cleanup/NotificationCoreRetentionWorker.java`, `application.yaml` | Retention configuration defaults: event 90d, notification history 180d, delivery intents 90d | Deployed overrides, all event/log stores |
| Billing | `billing/payment/CashfreePaymentGateway.java`, `application.yaml` | Customer details and payments sent to Cashfree gateway; default enabled=false | Gateway production status, tax/refund policy, accounting retention |
| Analytics | `unifydesk-website/app/layout.tsx` | GA4 loaded afterInteractive with no consent gate visible in layout | Live cookies/consent, settings and destinations |

## Release blockers
- P0: confirm public legal name, Udyam trade name and public address. Owner-provided routing is support@unifydesk.in (support/privacy/deletion/grievance/abuse) and reply@unifydesk.in (billing/business); inbox ownership, delivery and response processes remain untested.
- P0: retention and deletion matrix across database tables, object store, backups, provider-side data, invoices, suppression hashes, logs and exports. Test actual purge and retries.
- P0: verify Google actual OAuth scopes and compliance with Google API Services User Data Policy and Limited Use; assess restricted-scope security verification.
- P0: Meta public deletion mechanism and configured data deletion URL/callback.
- P0: verify GA4 and build an appropriate disclosure/consent mechanism; don't claim that one is live.
- P0: approve real subscription, renewal, PAYG, refund and cancellation terms against deployed billing.
- P1: inventory vendors, regions, agreements and cross-border flows. Verify security claims and incident contacts.
- P1: counsel review against India-only launch, consumer rules and DPDP phased commencement.

## Publication gates
All legal pages are draft and noindexed; do **not** deploy them as final policies, set provider verification URLs to them, or merge into dev before unresolved markers and reviews are cleared. Existing contact page has no public support channel. Footer/sitemap are unchanged intentionally. On approval, remove the draft warning, add verified dates and details, remove noindex, update contact page/footer/sitemap, test public URLs and app review links.

## Primary sources
- India DPDP Act phased commencement: https://www.meity.gov.in/static/uploads/2025/11/c56ceae6c383460ca69577428d36828b.pdf
- Google API Services User Data Policy: https://developers.google.com/terms/api-services-user-data-policy
- Google Workspace User Data/Developer Policy: https://developers.google.com/workspace/workspace-api-user-data-developer-policy
- Meta Platform Terms: https://developers.facebook.com/terms/
- India Consumer Protection rules: https://consumeraffairs.gov.in/pages/consumer-protection-acts
