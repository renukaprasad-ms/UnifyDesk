# UnifyDesk website SEO content round

Branch: `feature/website-seo-content-r1` from current `dev` frozen at `d391414b1dc379dd2328fed5fced5b69db5bd9ca`, after redesign PR #317 was merged. The remote commit uses that exact SHA as parent and the merged redesign tree as base. The PR targets `dev`; merge and deployment are separate review steps.

## Changes

- Solutions: practical support, outreach, owner and team-access examples.
- Integrations: provider roles, prerequisites, retained public rollout labels and workflow links. Inbox email is distinct from campaign delivery.
- Security: company membership/permissions, two-factor verification, session revocation and encrypted provider-credential boundaries, based on read-only source review.
- Pricing: platform entitlements, managed email/SMS usage, separate advertising spend, evaluation questions and visible FAQs. Existing ₹0/coming-soon direction retained; no paid price or exact limit invented.
- Reusable server-rendered channel-guide template, contextual links and native FAQ disclosures. Home/Product gain links below existing sections; heroes and tour retained.
- All six established titles and global schema retained. New pages have unique titles, descriptions, HTTPS canonicals, social metadata, one H1, WebPage and BreadcrumbList JSON-LD.
- Sitemap extended to 15 public routes with fixed significant-edit dates. `npm run check:seo` validates exported HTML. No domain-level `_redirects` rules added; Wrangler and real 404 configuration unchanged.
- Rendered image audit: all images have alt attributes. Empty brand marks accompany equivalent visible text and are intentional. [Claim and image review](claims.md).

## New routes

| Route | Distinct purpose | Local main-content words* |
| --- | --- | ---: |
| `/whatsapp-shared-inbox` | Team replies, number ownership and customer context | 643 |
| `/whatsapp-broadcast` | Consent, templates and outbound campaign review | 617 |
| `/sms-marketing` | Sender/template readiness, applicable DLT setup and usage | 631 |
| `/email-campaigns` | Verified sender/domain, campaign audience and managed delivery | 623 |
| `/omnichannel-inbox` | Cross-provider conversations, customer context and channel differences | 634 |

*Whitespace-delimited words in production `<main>`, including FAQ answers and contextual links, excluding scripts/navigation/footer. Counts describe output rather than an SEO target or a guarantee that crawler flags will disappear. Expanded pages: Solutions 484, Integrations 679, Pricing 583, Security 489 words. About/Contact remain concise.

## Files changed

All changes are under `unifydesk-website/`:

- New `app/seo-content.ts`, `app/seo-landing.tsx`, `app/channel-sections.tsx` and five `app/<channel-route>/page.tsx` entry points.
- Existing `app/page.tsx`, `app/product/page.tsx`, `app/solutions/page.tsx`, `app/integrations/page.tsx`, `app/security/page.tsx`, `app/pricing/page.tsx`, `app/globals.css`, `app/layout.tsx` and `app/sitemap.ts`.
- `scripts/check-seo.mjs`, `package.json`, latest `design-qa.md`, archived previous QA, source-review docs, static/responsive results and screenshot evidence.

Generated `out`, `.next`, dependencies and the temporary harness are excluded. No `unifydesk-app` edits or new runtime dependency.

## Validation

From `unifydesk-website/`:

```bash
npm ci
npm run typecheck
npm run build
npm run check:seo -- --report docs/seo-content-r1/static-seo-checks.json
npx wrangler deploy --dry-run
git diff --check
```

All passed locally. Next generated 24 static entries including metadata/framework routes; the sitemap contains 15 public website routes. Checks cover unique titles/descriptions, preserved main titles, canonicals, H1s, indexing metadata, JSON-LD, sitemap inclusion, internal routes/fragments and classified alt. [Static results](static-seo-checks.json). Final dry-run uses a clean generated export, without deployment.

Browser QA covers 48 route/viewport states, five guides visually on phones, source/final comparisons, FAQ disclosure, mobile menu/Escape/focus and tour navigation. [Design QA](../../design-qa.md) records the resolved Security opening-layout iteration. [Responsive measurements](responsive-checks.json).

Hosted CI results belong to the PR checks and remain separate from local validation. The earlier redesign's jobs failed before runner steps executed; that does not establish this round's CI result or its root cause. Inspect the new run before merge.

## Remaining deployment work

[Exact Cloudflare configuration](cloudflare-https.md) documents a scoped permanent redirect to HTTPS apex, preserving path/query and conditionally handling active `www`. Always Use HTTPS is an alternative when its zone-wide scope is intended. No dashboard setting was changed. The supplied HTTP 200/GSC audit is a baseline, not fresh production verification in this round.

After review and deployment: enable/verify the edge redirect; check paths, queries and loops; recrawl deployed pages; submit the updated sitemap; inspect five new HTTPS URLs in Google Search Console. Keep local validity, deployment, live-test pass and indexing as separate states. Provider readiness, public sign-up and paid pricing remain product launch work.
