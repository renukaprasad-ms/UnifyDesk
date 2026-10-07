# Website claim review

Read-only product evidence checked against frozen `dev` commit `d391414b1dc379dd2328fed5fced5b69db5bd9ca`. No `unifydesk-app` files were edited. Implementation evidence supports the described architecture, but does not establish public availability, a successful real provider run, certifications or production assurance.

## Provider wording

| Provider | Public website label retained | Role and evidence boundary |
| --- | --- | --- |
| WhatsApp | In development | Company-owned business account/number, conversation and outreach direction. Ownership validation and encrypted credential lifecycle are described in the WhatsApp architecture. |
| MSG91 | In development | Managed SMS direction. Provisioning, sender/template readiness and external delivery verification remain prerequisites. |
| MetaReach | Evaluation | No committed public rollout is claimed. |
| Gmail / Microsoft | In development | Mailbox inbox/sync/reply adapters. They are explicitly distinct from campaign delivery targets. |
| Amazon SES | In development | Managed campaign email direction with sender/domain readiness and usage billing. |
| SendGrid / Azure Email | Planned | Backend channel adapters exist; the unchanged website label describes additional public rollout choices, not absence of source implementation or production readiness. |
| Google Ads / Meta Ads | In development | Existing Ad Center direction retained; provider-native permissions and separate media spend. No new live advertising claim. |

Relevant files at the frozen SHA:

- `unifydesk-app/docs/core-messaging-runtime-r1/PROVIDER-READINESS.md`: source setup is not readiness evidence; runtime provider checks were not executed in the documented round. Gmail/Microsoft mailbox adapters are not campaign broadcast targets.
- `unifydesk-app/docs/email-provider-strategy.md`: mailbox connections, OAuth and sync/reply roles.
- `unifydesk-app/docs/whatsapp/ARCHITECTURE.md`: company authorization, ownership, provider validation and credential boundaries.
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/sendgrid/SendGridChannelProvider.java` and `broadcast/azure/AzureEmailChannelProvider.java`: additional delivery provider adapters, without evidence of public rollout.
- `unifydesk-app/backend/src/main/java/com/messaging/broadcast/msg91/Msg91ChannelProvider.java`: MSG91 SMS adapter.
- `unifydesk-app/backend/src/main/java/com/messaging/managedemail/control/service/ManagedEmailDomainService.java`: company-scoped sender/domain requests and verification state.

## Security and billing

- `CompanyAuthorizationService.java` checks active company membership, company status and role permissions. Copy describes company-scoped checks and access review without promising perfect isolation or an independent security certification.
- `AuthService.java` supports a two-factor challenge for enabled accounts and verified sensitive account changes. Copy does not claim authenticator apps or a specific second-factor mechanism.
- `UserSessionService.java` supports individual and user-wide revocation. The authentication service revokes sessions for applicable account changes.
- `ProviderCredentialService.java` encrypts stored provider payloads through the credential cipher and deletes stored credentials on disconnect. Copy does not invent a cipher algorithm, retention guarantee or certification.
- Existing billing architecture, managed sending direction and the prior website distinguish platform entitlements from managed email/SMS usage and external Google/Meta media spend. No paid amount, final limit, free delivery allowance or public purchase flow was introduced.

The company/auth/session/credential source paths above are under `unifydesk-app/backend/src/main/java/com/messaging/` with their respective `company/service/`, `auth/service/`, `session/service/` and `channel/credential/` directories. Earlier integration-status and security QA reports were treated as dated context, not current proof of release readiness.

## Content and image decisions

- The channel pages offer different tasks and prerequisites, with shared rendering rather than duplicated generic marketing copy. Examples are illustrative; no customer logos, testimonials, delivery rates or fabricated results were added.
- Guidance about audience consent, templates, unsubscribe processes and DLT is preparation advice. The website does not promise every workflow step is automated or that provider registration can be bypassed.
- Rendered images are the original `/logo-icon.svg`, beside a visible UnifyDesk name or in a brand link named “UnifyDesk home.” Header/footer each contribute one; the product tour adds one on Home/Product. `alt=""` avoids repeating that adjacent name. All image elements have an alt attribute; there are no informational raster images requiring a description in these reviewed states.
- [W3C guidance on functional images](https://www.w3.org/WAI/tutorials/images/functional/) allows an empty alt for an icon accompanying equivalent linked text. An automated crawler may continue to count these as empty; that count is not by itself an accessibility defect.
- Route-level WebPage/BreadcrumbList schema was added for the new pages. Existing Organization/WebSite/SoftwareApplication schema remains intact. FAQ answers are visible native disclosures; no FAQ rich-result eligibility or invented Offer/Review data is claimed.
- Sitemap dates remain fixed source dates. All existing website routes were part of the redesign merged on 7 October; this round's content and new routes were edited on the same date. Builds do not replace them with the current build time.
