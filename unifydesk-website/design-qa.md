# UnifyDesk SEO content round — design QA

final result: passed

Reviewed 7 October 2026 against the merged redesign at frozen `dev` commit `d391414b1dc379dd2328fed5fced5b69db5bd9ca`. This is a content extension. The original homepage split hero, product tour, assets, navigation, blue/white palette and dark Security hero are preserved. [Earlier redesign QA](docs/website-product-design-r2/design-qa.md) is archived.

## Findings and iteration history

- **Resolved P2 — Security opening composition.** The first implementation placed new company-boundary text before the original four control cards. [First comparison](docs/seo-content-r1/evidence/security-first-comparison.jpg) shows source cards replaced by text in the opening viewport, unnecessary drift from the retained design. Moved new explanations after the original cards. [Revised comparison](docs/seo-content-r1/evidence/security-comparison.jpg), at the same viewport and top-of-page state, restores the hierarchy. Rechecked the revised phone layout without overflow.
- **Accepted content expansion — Integrations.** A public-readiness explanation precedes the provider groups, and providers now have useful roles, prerequisites and workflow links. This intentionally moves the groups down while retaining hero, tokens, gutters and group styling. [Comparison](docs/seo-content-r1/evidence/integrations-comparison.jpg).
- No actionable P0, P1 or P2 findings remain. No new image generation or asset substitution was required.

## Source and rendered evidence

Source visual truth: [before-solutions.jpg](docs/seo-content-r1/evidence/before-solutions.jpg), captured before this round. Implementation: [after-solutions.jpg](docs/seo-content-r1/evidence/after-solutions.jpg). These were viewed together in [solutions-comparison.jpg](docs/seo-content-r1/evidence/solutions-comparison.jpg). [Pricing](docs/seo-content-r1/evidence/pricing-comparison.jpg), [Integrations](docs/seo-content-r1/evidence/integrations-comparison.jpg) and revised [Security](docs/seo-content-r1/evidence/security-comparison.jpg) were also reviewed as combined inputs; each contains source left and implementation right.

Desktop panels are 1348 × 926 pixels from the same 1363 × 936 CSS browser viewport, unauthenticated, top of page, collapsed navigation. Boards are 2720 × 984 pixels including label strip and 24 px gutter. Screenshots were not independently stretched or resampled; equal screenshot density applies to both sides. Scrollbar length reflects content expansion. The Next.js development indicator is tooling chrome.

Focused checks: [provider-details.jpg](docs/seo-content-r1/evidence/provider-details.jpg), 1260 × 1220, and [guide-details.jpg](docs/seo-content-r1/evidence/guide-details.jpg), 1260 × 1020, are equal-density crops of full-page browser captures. New copy has no old source counterpart and was judged against the existing type/card/token system. These readable crops supplement the full-view comparisons. [WhatsApp guide desktop](docs/seo-content-r1/evidence/whatsapp-inbox-desktop.jpg) shows the template opening. [Five guides on phones](docs/seo-content-r1/evidence/channel-guides-mobile.jpg) uses five original 393 × 830 px iframe captures.

## Required visual surfaces

| Surface | Result |
| --- | --- |
| Fonts and typography | Existing Inter/system sans-serif stack, weights, scale and fallbacks retained. Hero wrapping is consistent. Body copy, provider descriptions, labels and links are readable in focused captures. Brand capitalization corrected in guide FAQ headings. |
| Spacing and layout | Existing gutters, section spacing, card radii and buttons reused. Workflow/provider grids stack on phones. Security's initial card section restored. Added content uses existing split and soft sections. |
| Colors and tokens | Blue `#155eef`, ink `#14253b`, soft `#f4f7fb`, line `#dfe7f0` and dark `#0b1422` retained. New UI uses these tokens. |
| Image quality and assets | Original SVG mark and Lucide icons retained. Product tour remains native illustrative UI. No substitute artwork or fabricated customer marks. Adjacent brand marks correctly use empty alt. |
| Copy and content | Distinct tasks, prerequisites, examples, FAQs and links per guide. Availability and pre-launch pricing are explicit. Security wording follows source architecture without certifications or production guarantees. |

## Functional and responsive verification

- [Measurements](docs/seo-content-r1/responsive-checks.json): all 15 routes at 393 px and all 11 changed/new routes at 320, 768 and 1920 px: 48 states. Every document's `scrollWidth` equals `clientWidth`; each has one H1 and the intended route. Inner widths are 305/378/753/1905 px after scrollbars. This is a layout harness, not physical-device or cross-engine emulation.
- Routes were explicitly awaited by expected H1. An initial loop raced iframe navigation and measured old pages; those rows and images were discarded and replaced with route-confirmed evidence.
- Mobile menu opened, Escape closed it and restored trigger focus. WhatsApp FAQ expanded to its correct answer. Tour link reached `/product#product-tour` with the target present. Internal routes and fragment targets were checked against exported HTML.
- Rendered images all have alt attributes. Empty marks accompany the visible UnifyDesk name; Home/Product have an extra mark in the tour. No undescribed informational images in these states.
- Focus-visible styling, reduced motion, semantic headings and native details remain. High zoom, screen-reader use and every browser engine were not exhaustively tested.
- Current preview console review found browser-extension metadata errors without application runtime errors. Older entries belong to the earlier round. Root layout now includes Next.js's smooth-scroll hint; sticky-header auto-scroll warnings are non-blocking framework behavior.
- Dependency installation, type checking, production build, 15-route SEO checks, Wrangler deployment dry-run and whitespace checks passed locally. Preview was stopped for builds. Final export used clean generated directories; temporary harness removed before export and excluded from commit.

## Implementation checklist and limits

- [x] Preserve identity and opening composition.
- [x] Compare source/final together; resolve Security drift and recapture.
- [x] Check new guide visuals, responsive geometry and key interactions.
- [x] Verify metadata, routes, image classification and a clean static export.

Cloudflare redirects, live crawl results and Google Search Console indexing need post-deployment verification. The edge rule is documented, not enabled by this repository change. Public provider access, sign-up and final paid prices remain launch work.

final result: passed
