# AWS Activate Founders — UnifyDesk website readiness

Prepared: 19 October 2026. Scope: marketing website within `UnifyDesk/unifydesk-website/`, not the AWS application itself.
This checklist does **not** promise AWS Activate approval and does not substitute for legal counsel or live operational checks.

## Confirmed from founder-supplied evidence

- [x] AWS account identity verification shows **Verified** (ownership type: Individual; purpose: Business operations). Using individual identity is consistent with a sole proprietorship when the details are truthful.
- [x] AWS billing screenshot shows UPI AutoPay enabled and an existing **$100** credit balance. This does **not** prove Activate Founders approval, nor prove that the account is eligible for AWS Activate.
- [x] Working marketing site at https://unifydesk.in describing a B2B SaaS product under development.
- [x] UnifyDesk has a business-domain contact route advertised at support@unifydesk.in and reply@unifydesk.in.
- [x] About and Contact page source describes the business as an independently developed, self-funded India-based sole proprietorship preparing for early access.

## Verification still required before applying

- [ ] Confirm whether the *specific AWS account* linked for Activate is on an eligible **Paid Tier** plan; this cannot be determined from the billing screenshot alone.
- [ ] Confirm the AWS account/Activate profile email matches the intended UnifyDesk identity (cloud.admin@unifydesk.in or other authorized mailbox).
- [ ] Confirm the website support and reply inboxes actually receive mail and can respond.
- [ ] Confirm the legal business name, date founded, Udyam trade name, and application description match available records; do **not** claim incorporation or outside investment.
- [ ] Confirm whether any AWS Activate credits were previously received; existing $100 credits could be a separate promotion.
- [ ] Confirm working payment method and add spend limits and alerts. The provided screenshot shows **no limit set** for the UnifyDesk project.
- [ ] Review and legally approve Privacy and Terms before they are treated as effective; draft legal content is deliberately noindex and excluded from sitemap.
- [ ] Verify deployed website, analytics choice, desktop/mobile presentation, all mailto links, and that no user-identifying AWS screenshot is published.

## Website privacy / legal handling

- Optional Google Analytics is gated behind explicit Allow analytics choice. Test in a clean browser: no gtag network requests before consent or on rejection, scripts load after acceptance, and settings control works on a return visit.
- Legal pages remain non-binding review drafts until real operator information, notices, billing rules, subprocessor list, retention and legal obligations are approved.
- Razorpay migration references are descriptive development notes, **not** a claim of production payment support.

## Smoke tests

From `unifydesk-website/`:

```sh
npm ci
npm run typecheck
npm run build
npm run check:seo
```

Manual verification: open `/about`, `/contact`, `/privacy`, `/terms`, `/cookies`; check contact links; opt in/out analytics; review mobile; validate sitemap only lists indexable public pages.
