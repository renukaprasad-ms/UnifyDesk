# Current Amazon SES Research for Managed Email

Research date: 2026-09-26

This document records the AWS facts that drive the architecture. Re-check these sources before a production rollout because SES features, service quotas, SDK surface area, and pricing can change.

## 1. SES Tenant Management

Official AWS guide:
https://docs.aws.amazon.com/ses/latest/dg/tenants.html

SES tenant management is designed for multi-tenant / ISV use cases. A tenant is a logical container that can be associated with SES resources and used for tenant-specific reputation and suppression behavior.

Key architecture implications:

- a tenant is not a separate AWS account;
- UnifyDesk still owns the AWS account and underlying SES quota/reputation exposure;
- identities and configuration sets are associated with the tenant;
- tenant sending status/reputation can be used as an isolation and enforcement signal;
- tenant-scoped suppression is available;
- tenant management improves attribution/isolation but does not remove the need for UnifyDesk-wide anti-abuse and account-health controls.

Decision:
One UnifyDesk company maps to one SES tenant for MVP.

We do not expose tenant names/IDs to customers.

## 2. Tenant resource association

SES tenant documentation and SES v2 API support associating SES resources with a tenant.

Managed Email needs, at minimum:

- the company's sending identity/identities;
- the company's configuration set.

Provisioning is not complete until the required associations are present.

The implementation must use the current SDK/API shape rather than assuming an older SES v2 SDK exposes tenant methods. The repository currently pins AWS SDK 2.31.77; B1 must upgrade to a current compatible AWS SDK release if required and compile/test the tenant API calls.

## 3. Domain identity and Easy DKIM

Official AWS documentation:
https://docs.aws.amazon.com/ses/latest/dg/creating-identities.html
https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dkim-easy.html

Production approach:

- create a domain identity, not one identity per From address;
- use Easy DKIM;
- use the current SES-returned DKIM tokens/records;
- 2048-bit DKIM is the current preferred/default capability where supported;
- persist the exact records returned by the selected SES region;
- poll GetEmailIdentity / current SES identity state for verification and DKIM status.

A verified domain identity can authorize sending from addresses beneath that domain. This does not replace UnifyDesk's own ownership check: a sender is valid only when its exact domain is an ACTIVE ManagedEmailDomain owned by the same company.

For a customer domain, the required MVP onboarding records are the Easy DKIM CNAME records returned by SES.

Do not manufacture fixed CNAME target suffixes in application code if SES returns the required record/hosted-zone information. Persist/render the provider-returned values.

## 4. SES identities are regional

Official AWS documentation:
https://docs.aws.amazon.com/ses/latest/dg/regions.html

SES identities, quotas, suppression behavior, event resources and related configuration are regional.

Implication:
MVP uses one configured SES region.

A multi-region design would require explicit identity/resource provisioning in each region rather than assuming verification follows automatically.

## 5. Custom MAIL FROM

Official AWS documentation:
https://docs.aws.amazon.com/ses/latest/dg/mail-from.html

By default SES uses an amazonses.com MAIL FROM domain. This supports SPF authentication without requiring a TXT SPF record on the customer's visible From domain.

A custom MAIL FROM domain requires additional DNS configuration, including the SES feedback MX and SPF TXT record, and introduces behavior choices if its MX is not configured correctly.

Implication:
Custom MAIL FROM is not required for MVP.

Easy DKIM supplies the aligned authentication path used for DMARC. Custom MAIL FROM remains a later deliverability feature.

## 6. SPF

With default SES MAIL FROM:
- SES handles SPF authentication through its MAIL FROM domain;
- UnifyDesk must not tell own-domain customers that an SPF record on their From domain is required for MVP;
- adding another SPF TXT record to a domain that already has SPF can break SPF, so arbitrary SPF mutation is unsafe.

If custom MAIL FROM is added later, follow the exact current AWS MX/TXT instructions for that MAIL FROM subdomain.

## 7. DMARC

Official AWS documentation:
https://docs.aws.amazon.com/ses/latest/dg/send-email-authentication-dmarc.html

DMARC can pass when either an aligned DKIM signature or aligned SPF authentication passes.

MVP:
- Easy DKIM is required and is the primary alignment path;
- own-domain DMARC is recommended/assessed, not a hard verification prerequisite;
- customer DMARC must never be overwritten;
- hosted UnifyDesk subdomains get a platform-controlled explicit DMARC policy.

The UI should distinguish:
"required to activate" from "recommended for deliverability/security."

## 8. Configuration Sets

Official AWS event publishing documentation:
https://docs.aws.amazon.com/ses/latest/dg/monitor-sending-activity-using-notifications.html
https://docs.aws.amazon.com/ses/latest/dg/event-publishing-add-event-destination.html

A configuration set is specified when sending and can carry event destinations and future sending/reputation configuration.

Decision:
One configuration set per company/SES tenant.

This provides:
- clean event attribution;
- tenant resource association;
- future sending-pool assignment;
- company-level operational diagnostics.

## 9. SES event types

Official AWS event contents:
https://docs.aws.amazon.com/ses/latest/dg/event-publishing-retrieving-sns-contents.html

Relevant current events include:

- Send
- Delivery
- Bounce
- Complaint
- Reject
- Open
- Click
- Rendering Failure
- DeliveryDelay
- Subscription

Not all events should map to a BroadcastRecipient enum.

Managed Email stores an idempotent normalized event ledger and a delivery/engagement projection. Only state-changing events that belong in the existing Broadcast semantics, such as a hard bounce, update the core recipient state.

## 10. Event destinations

SES configuration sets currently support destinations including:

- CloudWatch
- Kinesis Data Firehose
- Amazon SNS
- Amazon EventBridge

MVP decision:
SES configuration set -> shared platform SNS topic -> authenticated HTTPS endpoint.

Reasons:
- direct SES integration;
- one platform channel rather than one customer callback;
- compatible with the current modular monolith without introducing another AWS queue SDK into B2;
- SNS retry delivery;
- signed SNS messages can be cryptographically verified;
- event normalization is independent of future transport changes.

Future option:
SNS/SQS or EventBridge/SQS fan-out for a more durable buffer, while keeping the same normalized event contract.

## 11. SNS callback trust

Official SNS message-signing guidance:
https://docs.aws.amazon.com/sns/latest/dg/sns-verify-signature-of-message.html

The application must not treat a JSON POST as trusted because it claims to be from SES/SNS.

Required:
- verify SNS signature;
- validate SigningCertURL scheme/host;
- allow only configured TopicArn;
- validate timestamp/replay window;
- idempotently persist MessageId/event identity;
- validate subscription confirmation URLs before following them if auto-confirmation is enabled;
- do not accept company identity from query/path parameters as event truth.

## 12. Suppression

Official AWS guidance:
https://docs.aws.amazon.com/ses/latest/dg/sending-email-suppression-list.html
https://docs.aws.amazon.com/ses/latest/dg/tenants.html

Managed Email uses:
- UnifyDesk application suppression as product truth;
- SES tenant-scoped suppression for BOUNCE and COMPLAINT as provider defense-in-depth.

Tenant-scoped suppression is important because one company's bounce/complaint recipient list should not become another company's product-level suppression list.

Application-level unsubscribe/admin/compliance suppression remains separate from SES bounce/complaint suppression semantics.

## 13. Sending quotas

Official AWS documentation:
https://docs.aws.amazon.com/ses/latest/dg/manage-sending-quotas.html
https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html

SES quota properties include:
- maximum messages/recipients over a rolling 24-hour period;
- maximum sending rate;
- sandbox restrictions;
- region-specific quota values.

AWS states quotas are regional. Sandbox values are much smaller and production access must be granted before normal Managed Email launch.

Implication:
- UnifyDesk's platform account quota is shared by all Managed Email companies in the selected region;
- a connection-scoped limiter alone is unsafe;
- B2 must enforce a platform/account bucket plus per-company/tier controls.

Do not hard-code AWS account quota numbers. Read/monitor the live account quota and apply a platform safety ceiling.

## 14. Reputation

Official SES reputation documentation:
https://docs.aws.amazon.com/ses/latest/dg/reputation-dashboard.html

Relevant signals:
- bounce rate;
- complaint rate;
- account sending health;
- tenant sending/reputation state where available;
- provider enforcement/sending pauses.

The product must use stricter internal configurable thresholds and graduated containment rather than waiting for AWS account enforcement.

Because exact acceptable rates and AWS enforcement policies may evolve, the architecture treats threshold values as audited operational configuration, not schema constants.

## 15. Virtual Deliverability Manager

Official AWS documentation:
https://docs.aws.amazon.com/ses/latest/dg/vdm.html

VDM can provide additional deliverability insights/optimization. It is useful for operations and future premium visibility but is not required to establish MVP correctness.

The architecture should not couple core send eligibility or billing correctness to VDM availability.

## 16. Shared vs dedicated IP

Official AWS dedicated IP documentation:
https://docs.aws.amazon.com/ses/latest/dg/dedicated-ip.html

SES supports shared infrastructure and dedicated IP options, including managed dedicated IP capability.

Decision:
MVP uses shared SES sending infrastructure.

Future:
assign a tenant/company configuration set to a dedicated/managed sending pool for sufficiently large and healthy customers.

Dedicated IPs are not a substitute for list quality, authentication, abuse prevention, or reputation monitoring.

## 17. Provider acceptance vs delivery

SES SendEmail returning a provider message ID means SES accepted the request for processing. It does not prove inbox delivery.

Architecture terminology:

ACCEPTED_BY_PROVIDER
!=
DELIVERED

Billing boundary:
provider acceptance.

Deliverability analytics:
asynchronous delivery/bounce/complaint/etc events.

## 18. Region and disaster recovery

Because identities/configuration/quota are regional, transparent automatic failover to another region is not an MVP feature.

A future DR design must deliberately provision:
- identities/DKIM;
- tenants;
- configuration sets;
- quotas;
- event destinations;
- suppression policy;
- credentials/roles;
- routing.

Failing to do so risks a "failover" region that cannot legally/authentically send.

## 19. Pricing and service limits

SES pricing and service quotas can change.

References:
https://aws.amazon.com/ses/pricing/
https://docs.aws.amazon.com/ses/latest/dg/quotas.html

Do not encode:
- per-email AWS prices;
- maximum tenant count;
- identity count;
- dedicated IP prices;
- exact account quota

as immutable product assumptions.

Expose them as operational/cost inputs where needed and request quota increases ahead of growth.

## 20. Research-derived architecture conclusions

1. Company -> SES Tenant is appropriate for current SES capabilities.
2. SES tenant isolation is useful but does not replace platform anti-abuse.
3. Domain identities + Easy DKIM are the MVP identity model.
4. One company configuration set is the correct event/resource boundary.
5. Custom MAIL FROM is later, not MVP.
6. Own-domain DMARC is recommended, not activation-blocking.
7. Hosted subdomains get platform-managed DKIM and DMARC.
8. Single-region MVP minimizes regional identity/quota complexity.
9. Account-level distributed limiting is mandatory.
10. Provider event processing must be authenticated, idempotent, company-safe, and out-of-order safe.
