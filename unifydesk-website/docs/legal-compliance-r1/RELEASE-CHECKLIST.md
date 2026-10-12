# India Early Access legal release checklist

Draft changes are NOT ready to deploy. Reviewer must sign off each item:
- [ ] Verify sole proprietor legal identity, correspondence address and business name. Owner-provided emails: support@unifydesk.in (support/privacy/deletion/grievance/abuse), reply@unifydesk.in (billing/business). Test mailbox ownership, inbound/outbound delivery, escalation and responses before publication.
- [ ] Validate retention for DB, contact suppression, provider tokens, object files, imports/exports, payments, logs and backups; run deletion test incl. failure/retry and provider-side actions.
- [ ] Confirm Google restricted Gmail OAuth scopes, Limited Use, privacy review and transfers.
- [ ] Configure Meta public data deletion route and accessible request processing.
- [ ] Audit actual GA4 cookies and need for consent UX under applicable law.
- [ ] Verify actual suppliers, India/global locations, vendor terms and DPA role split.
- [ ] Confirm Cashfree enablement and subscription/managed usage prices, refunds, mandates and invoices.
- [ ] Complete Indian counsel review of privacy, consumer rules, contracts and statutory phase dates.
- [ ] Replace all remaining [CONFIRM ...] placeholders (including legal address) with verified facts and effective dates. Owner-provided emails are not independently verified.
- [ ] Remove draft banner/noindex and link approved legal pages in footer, contact and sitemap.
- [ ] Run npm ci, npm run typecheck, npm run build, npm run check:seo.
- [ ] Test live public URLs in browser, mobile, Meta crawler and Google consent setup; confirm request emails deliver.

Never invent fixed retention guarantees, compliance certification or business contact details.
