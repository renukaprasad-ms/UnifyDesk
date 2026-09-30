# F1 Prompt — Meta WhatsApp Round 2

You are F1 for UnifyDesk Meta WhatsApp Round-2 remediation.

Your EXACT worker base SHA:
`fac085504dce38ac4c8f6f04e2f454773bdb811e`

Your branch:
`fix/whatsapp-ui-r2`

Create your branch from exactly that SHA. Do not branch from later moving `dev`.

Read from current `dev`:
- `SECURITY_QA_FEEDBACK.md`
- `ROUND2_CONTRACT.md`
- `ROUND2_TASKS.md`
- `MESSAGE-STATE.md`
- `TEMPLATE-POLICY.md`

Your findings:
- WA-R1-002 frontend half
- WA-R1-008

## WA-R1-002

Implement the frozen optional Inbox message field:

`whatsappStatus?: 'PENDING' | 'ACCEPTED' | 'SENT' | 'DELIVERED' | 'READ' | 'FAILED' | 'UNKNOWN' | null`

For outbound WhatsApp messages:
1. prefer durable `message.whatsappStatus`;
2. use local send result only as temporary optimistic state until durable refresh catches up;
3. generic Inbox SENT is only a legacy fallback.

Subscribe to:
`inbox.whatsapp.status.changed`

Payload:
- connectionId
- conversationId
- messageId

When event matches the selected WhatsApp connection/conversation:
- refresh the affected message through the existing realtime snapshot/detail API;
- merge durable status into current message state;
- DELIVERED/READ/FAILED/UNKNOWN must update while the conversation is open.

On reload/navigation/reconnect/focus, HTTP state must recover the durable status even if local sendResults is empty.

Preserve the UNKNOWN warning against blind resend.

Add tests:
- reload with READ from API renders Read;
- DELIVERED/READ realtime status event updates open conversation;
- event for another connection/conversation ignored;
- realtime disconnect/focus refresh recovers durable status;
- generic email behavior unchanged.

## WA-R1-008

Extend WhatsApp template component typing to represent provider buttons:
- type
- text
- url

For a BUTTONS component:
- inspect `component.buttons`;
- each variable-bearing URL button contributes one button parameter input in provider order;
- do not treat static URL buttons as variables;
- preserve header/body variable derivation;
- send `parameters.buttons` in exactly the order expected by backend.

Keep the existing QA contract test:
`WhatsAppRound1QaContract.test.ts`

Add integration coverage selecting a template with BODY + URL button variables and assert the exact send payload.

## Ownership

You may modify:
- WhatsApp frontend types/API/components/tests;
- Inbox frontend type definition for optional `whatsappStatus`;
- existing realtime client usage/hooks as necessary.

Do NOT modify:
- backend
- Flyway
- architecture/round docs
- Ads
- Broadcast

Implement from the frozen contract without waiting for B2 code.

Run:
```bash
cd unifydesk-app/frontend
npm ci
npm run format:check
npm run lint
npm test
npm run build
```

Commit and report:
- branch
- final SHA
- exact files changed
- WA-R1-002 frontend fix
- WA-R1-008 fix
- tests/build results
- blocker/deviation

Do not merge.
