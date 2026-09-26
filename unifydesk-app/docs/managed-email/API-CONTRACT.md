# Managed Email Frozen API Contract

Status: Frozen for Round 1  
Audience: B1, B2, F1  
Provider abstraction: customer-facing API says Managed Email / UnifyDesk, not Amazon SES.

All company endpoints require current authenticated user plus company permission checks on the backend.

Base path: /api/v1/companies/{companyId}/managed-email

All IDs are serialized as strings in frontend types, following existing frontend conventions.

## 1. GET /overview

Permission: BROADCAST_READ

Response data:

    {
      "enabled": true,
      "serviceStatus": "ACTIVE",
      "sendingStatus": "ENABLED",
      "regionLabel": "Managed by UnifyDesk",
      "domainLimit": 5,
      "senderLimit": 20,
      "globalNotice": null
    }

Customer API must not expose AWS region, SES tenant name, configuration-set name, AWS account ID, or platform account quotas.

ServiceStatus:
- NOT_CONFIGURED
- PROVISIONING
- ACTIVE
- SUSPENDED
- ERROR
- DISABLED

SendingStatus:
- ENABLED
- DISABLED
- PROVIDER_DISABLED

## 2. GET /domains

Permission: BROADCAST_READ

Returns ManagedEmailDomainResponse values with:

- id
- type
- domain
- status
- verificationStatus
- dkimStatus
- dmarcStatus
- dmarcPolicy
- dnsRecords
- activatedAt
- lastCheckedAt

DNS record response fields:

- id
- purpose
- type
- name
- value
- priority
- required
- status

Do not include provider identity names beyond DNS values the customer must publish.

DomainType:
- CUSTOMER_DOMAIN
- UNIFYDESK_HOSTED

DomainStatus:
- PROVISIONING
- PENDING_DNS
- VERIFYING
- ACTIVE
- SUSPENDED_VERIFICATION
- SUSPENDED_POLICY
- ERROR
- DELETING
- DELETED
- RETIRED

## 3. POST /domains/customer

Permission: BROADCAST_MODIFY

Request:

    {
      "domain": "example.com",
      "idempotencyKey": "client-generated-uuid-or-opaque-key"
    }

Validation:
- domain only, no scheme/path/email;
- IDNA normalized;
- not a public suffix;
- caller's company may not substitute another company;
- global active domain claim uniqueness;
- plan domain entitlement;
- idempotency.

Response:
HTTP 202 for a new/provisioning resource, or 200 for an idempotent replay of the same resource.

Returns ManagedEmailDomainResponse.

F1 must support PROVISIONING before DNS records exist and then refresh/poll.

## 4. POST /domains/hosted

Permission: BROADCAST_MODIFY

Request:

    {
      "idempotencyKey": "..."
    }

Round 1 does not accept a customer-selected arbitrary hosted prefix. B1 generates/reserves the safe hosted name from company metadata plus opaque suffix.

Response:
202/200 ManagedEmailDomainResponse.

Typical transition:
PROVISIONING -> VERIFYING -> ACTIVE

No DNS action is required from the customer.

## 5. GET /domains/{domainId}

Permission: BROADCAST_READ

Critical lookup rule:
query by domainId AND companyId.

A domain belonging to another company must never return its DNS records.

Returns ManagedEmailDomainResponse.

## 6. POST /domains/{domainId}/refresh

Permission: BROADCAST_MODIFY

Rules:
- domainId AND companyId ownership;
- rate-limit manual refresh;
- no long polling;
- may enqueue refresh and return 202;
- repeated refresh is idempotent.

Returns ManagedEmailDomainResponse.

## 7. DELETE /domains/{domainId}

Permission: BROADCAST_MODIFY

Rules:
- company scoped;
- make domain and bound senders non-sendable immediately;
- asynchronous provider cleanup;
- preserve historical Broadcast rows;
- hosted names remain permanently reserved.

Response: 202 with latest domain/operation state.

## 8. POST /senders

Permission: BROADCAST_MODIFY

Request:

    {
      "domainId": "123",
      "localPart": "offers",
      "displayName": "XYZ Offers",
      "replyToAddress": "support@example.com"
    }

Rules:
- load domain by id AND companyId;
- require domain ACTIVE;
- construct From server-side;
- caller cannot submit arbitrary full fromAddress;
- sender domain exactly equals ManagedEmailDomain.normalizedDomain;
- enforce sender entitlement;
- create/reuse the company's one MESSAGING_MANAGED AMAZON_SES connection;
- create existing BroadcastSender + SenderIdentity + managed binding.

Response HTTP 201 should remain compatible with the existing BroadcastSender shape and add:

- managementMode = MESSAGING_MANAGED
- connectionName = UnifyDesk Managed Email
- region = MANAGED in customer-facing response
- managedDomainId
- managedDomain

F1 must display provider as "UnifyDesk Managed Email", not "Amazon SES", when managementMode is MESSAGING_MANAGED.

## 9. GET /senders

Permission: BROADCAST_READ

Returns only MESSAGING_MANAGED senders belonging to the company.

The existing /broadcast/senders endpoint continues to return all Broadcast senders for composer compatibility.

## 10. DELETE /senders/{senderId}

Permission: BROADCAST_MODIFY

Rules:
- lookup sender and managed binding by senderId AND companyId;
- disable rather than destroying historical references;
- never affect another company.

Response 200/202.

## 11. GET /usage

Permission: BROADCAST_READ

B2 contract.

Response fields:
- periodStart
- periodEndExclusive
- accepted
- reserved
- adjustments
- billable
- limit
- remaining
- limitReached

A limit of -1 means unlimited only when the plan explicitly defines unlimited.

## 12. GET /deliverability

Permission: BROADCAST_READ

B2 contract.

Returns safe aggregate operational fields:
- status
- windowStart
- windowEnd
- accepted
- hardBounces
- complaints
- hardBounceRate
- complaintRate
- sendingRestricted
- notice

Do not expose AWS account reputation or other tenants.

Status:
- INSUFFICIENT_DATA
- HEALTHY
- WARNING
- THROTTLED
- SUSPENDED

## 13. Existing Broadcast sender contract

Existing composer/API continues to consume /broadcast/senders.

For a managed sender it receives:
- provider = AMAZON_SES
- managementMode = MESSAGING_MANAGED
- normal sender ID/fromAddress/display name
- sendingEnabled reflecting local eligibility.

This keeps BroadcastMessage senderId unchanged.

No new managed BroadcastMessage type is introduced.

## 14. Error contract

Use existing ApiResponse/error conventions.

Stable machine-readable codes should be added where the current exception framework permits:

- MANAGED_EMAIL_NOT_ENABLED
- MANAGED_EMAIL_DOMAIN_LIMIT_REACHED
- MANAGED_EMAIL_SENDER_LIMIT_REACHED
- MANAGED_EMAIL_DOMAIN_ALREADY_CLAIMED
- MANAGED_EMAIL_DOMAIN_NOT_ACTIVE
- MANAGED_EMAIL_DOMAIN_VERIFICATION_REQUIRED
- MANAGED_EMAIL_SENDING_SUSPENDED
- MANAGED_EMAIL_MONTHLY_LIMIT_REACHED
- MANAGED_EMAIL_RATE_LIMITED
- MANAGED_EMAIL_CAMPAIGN_LIMIT_EXCEEDED
- MANAGED_EMAIL_PLATFORM_DISABLED
- MANAGED_EMAIL_PROVIDER_UNAVAILABLE

Customer messages say UnifyDesk Managed Email, not SES/AWS, except DNS values that naturally contain provider-owned targets.

## 15. Polling contract

F1 may poll a non-terminal domain while its setup page is open.

Recommended behavior:
- initial interval 3 to 5 seconds;
- back off toward 15 to 30 seconds;
- stop when ACTIVE/terminal or page closes;
- manual refresh separately rate-limited.

Do not globally poll every domain.

## 16. Frontend flow contract

Connections -> Broadcast Email exposes two paths.

Primary:
Use UnifyDesk Managed Email

Secondary:
Connect my own provider

Managed Email:
- choose "Use my own domain" or "Use an UnifyDesk domain";
- own domain: enter domain -> required DNS -> verification -> ACTIVE -> create sender;
- hosted: create -> automatic setup progress -> ACTIVE -> create sender;
- active domain page lists senders and create-sender action;
- warnings distinguish required actions from recommended deliverability actions.

The existing provider picker remains available under "Connect my own provider."

## 17. Contract ownership

B1 owns endpoints 1 through 10. Overview is control-plane only; usage is always fetched from B2 /usage so B1 and B2 have no Java-level dependency.

B2 owns:
- /usage
- /deliverability
- runtime send guards
- events
- metering
- reputation/abuse runtime controls.

F1 implements this contract without importing backend Java artifacts or waiting for backend branches.

If implementation finds the frozen contract technically impossible, the worker documents the incompatibility for Architecture Lead review. It must not silently invent a different API.
