# Managed SMS HTTP Contract

Status: FROZEN for Round 1  
Base path: /api/companies/{companyId}/managed-sms

All endpoints require authenticated company access. IDs are strings in frontend JSON where existing Snowflake-ID conventions require it.

## Customer status

GET /status

Response:
{
  "enabled": true,
  "state": "ACTION_REQUIRED|WAITING_DLT_APPROVAL|WAITING_UNIFYDESK_REVIEW|PROVIDER_SYNCING|READY|REJECTED|SUSPENDED",
  "nextAction": "string|null",
  "dltProfile": { "id":"...", "peIdMasked":"...", "state":"...", "chainState":"..." } | null,
  "readyHeaderCount": 0,
  "readyTemplateCount": 0,
  "sendingEnabled": false,
  "usage": { "periodStart":"...", "periodEnd":"...", "acceptedSegments":0, "limit":null }
}

No provider account/client/AuthKey/Flow IDs appear here.

## DLT profile

POST /dlt-profile
{
  "legalName":"Acme Pvt Ltd",
  "dltPlatform":"AIRTEL|JIO|VIL|BSNL|OTHER",
  "peId":"1234567890",
  "tmChainReference":"optional",
  "evidence": { ...non-secret references... }
}

GET /dlt-profile
PATCH /dlt-profile/{profileId}
POST /dlt-profile/{profileId}/submit
POST /dlt-profile/{profileId}/refresh

Refresh never trusts client status; it triggers provider/manual verification workflow.

## Headers

GET /headers
POST /headers
{
  "header":"ACMEIN",
  "messageCategory":"PROMOTIONAL|SERVICE|TRANSACTIONAL|GOVERNMENT"
}

GET /headers/{headerId}
POST /headers/{headerId}/submit
POST /headers/{headerId}/refresh
DELETE /headers/{headerId}

Delete is archive/disable if historically referenced.

Header response:
{
 "id":"...",
 "header":"ACMEIN",
 "messageCategory":"SERVICE",
 "state":"WAITING_UNIFYDESK_REVIEW",
 "providerSyncState":"NOT_STARTED|SYNCING|READY|FAILED",
 "actionRequired":null,
 "updatedAt":"..."
}

## Templates

GET /templates
POST /templates
{
 "name":"Order shipped",
 "headerIds":["..."],
 "messageCategory":"SERVICE",
 "dltTemplateId":"1107...",
 "bodyText":"Your order {#var#} has shipped...",
 "variables":[
   {"key":"order_id","type":"ALPHANUMERIC","maxLength":30}
 ],
 "ctaPolicy":{
   "urlVariables":[],
   "callbackVariables":[]
 },
 "consentPolicy":null,
 "optOutPolicy":null
}

POST always creates a logical template + draft/verification version. Editing approved content creates a new version.

GET /templates/{templateId}
POST /templates/{templateId}/versions
POST /templates/{templateId}/versions/{versionId}/submit
POST /templates/{templateId}/versions/{versionId}/refresh
POST /templates/{templateId}/versions/{versionId}/archive

Customer response includes DLT Template ID because it is the customer's compliance identifier, but never provider Flow/template refs.

Template response includes:
{
 "id":"...",
 "name":"Order shipped",
 "messageCategory":"SERVICE",
 "state":"READY",
 "activeVersion":{
   "id":"...",
   "version":3,
   "dltTemplateId":"1107...",
   "bodyText":"...",
   "variables":[...],
   "allowedHeaderIds":["..."],
   "providerSyncState":"READY"
 }
}

## Managed sender creation

POST /senders
{ "headerId":"...", "displayName":"optional customer label" }

Response is the normal BroadcastSender shape, with:
- provider = MSG91_SMS
- managementMode = MESSAGING_MANAGED
- fromAddress = approved Header
- provider-facing refs omitted.

GET /senders may be implemented by existing Broadcast sender endpoint; F1 may consume either existing sender list or this convenience endpoint as frozen in TASKS.

## Usage and safety — B2

GET /usage
Response:
{
 "periodStart":"...",
 "periodEnd":"...",
 "acceptedSegments":1234,
 "reservedSegments":20,
 "unknownSegments":3,
 "limit":50000,
 "remaining":48743,
 "sendingEnabled":true
}

GET /delivery-health
Response exposes customer-safe aggregate status only:
{
 "status":"HEALTHY|DEGRADED|SUSPENDED",
 "delivered":1000,
 "failed":12,
 "rejected":3,
 "unknown":1,
 "actionRequired":null
}

No shared provider wallet, account topology, global capacity or other tenants' information.

## Broadcast contract changes

For CUSTOMER_MANAGED SMS existing contract remains valid.

For MESSAGING_MANAGED MSG91_SMS create/update request:
{
 "senderId":"...",
 "managedSmsTemplateId":"...",
 "templateVariables":{
   "order_id":"A123"
 }
}

Rules:
- bodyText is optional/ignored for managed SMS and must not be authoritative.
- managedSmsTemplateId is required for managed SMS.
- templateVariables keys must be declared in active version.
- selected sender Header must be allowed for selected template version.
- server renders and stores the compliant body.
- scratch SMS is rejected.

Broadcast response adds:
{
 "managedSmsTemplateId":"...|null",
 "managedSmsTemplateVersion":3|null
}

## Preview

POST /templates/{templateId}/preview
{
 "headerId":"...",
 "variables":{"order_id":"A123"}
}

Returns customer-safe rendered body plus validation errors. Preview does not prove delivery eligibility; launch revalidates every gate.

## Admin/manual verification

Admin endpoints are platform-admin scoped, not company-user scoped:
- GET /api/platform/managed-sms/review-queue
- POST /api/platform/managed-sms/dlt-profiles/{id}/decision
- POST /api/platform/managed-sms/headers/{id}/decision
- POST /api/platform/managed-sms/templates/{versionId}/decision
- POST /api/platform/managed-sms/tenants/{id}/suspend
- POST /api/platform/managed-sms/tenants/{id}/resume

Every decision requires reason/audit actor and optimistic-lock/version protection.

## Error contract

Use existing API error envelope. Stable managed error codes include:
MANAGED_SMS_NOT_READY
MANAGED_SMS_DLT_PROFILE_REQUIRED
MANAGED_SMS_TM_CHAIN_REQUIRED
MANAGED_SMS_HEADER_NOT_READY
MANAGED_SMS_TEMPLATE_NOT_READY
MANAGED_SMS_TEMPLATE_HEADER_MISMATCH
MANAGED_SMS_VARIABLE_INVALID
MANAGED_SMS_CTA_NOT_APPROVED
MANAGED_SMS_COMPLIANCE_STALE
MANAGED_SMS_SUSPENDED
MANAGED_SMS_LIMIT_REACHED
MANAGED_SMS_PROVIDER_CAPACITY_UNAVAILABLE

Frontend must use user-safe messages and never surface raw MSG91 payloads.
