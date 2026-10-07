# Canonical HTTPS at the Cloudflare edge

Status: configuration instructions prepared; no Cloudflare setting has been changed and no live redirect result is claimed.

## Recommended: a scoped Single Redirect

This website is a Next.js static export deployed as Cloudflare Workers assets. Keep `wrangler.jsonc` unchanged. Do not add a domain-level source rule to `public/_redirects`. Workers assets support path-based source rules and absolute destinations, but domain-level source rules are unsupported. A catch-all path rule also cannot distinguish HTTP from HTTPS and could loop.

1. Open the Cloudflare zone for `unifydesk.in`. Confirm the apex is routed through Cloudflare and has an active HTTPS edge certificate. SSL/TLS mode must not be Off.
2. If `www.unifydesk.in` is active, confirm its DNS is proxied through Cloudflare and its HTTPS edge certificate is active. If `www` is not used, leave it out of the rule rather than creating an unrequested hostname. To intentionally activate it later, configure proxied DNS and certificate coverage before enabling that branch of the rule.
3. Go to **Rules → Redirect Rules → Single Redirects → Create rule**. Use a custom filter expression:

```text
(http.host eq "unifydesk.in" and not ssl) or (http.host eq "www.unifydesk.in")
```

For an inactive `www` hostname, use only:

```text
http.host eq "unifydesk.in" and not ssl
```

4. Set the redirect action to **Dynamic**, with this target expression:

```text
concat("https://unifydesk.in", http.request.uri.path)
```

5. Set **Status code: 301** and **Preserve query string: Enabled**. The target expression includes the path; the separate preserve option includes the query. Do not append the query a second time.
6. Place this rule ahead of overlapping marketing-host redirects. Review existing rules for conflicts before enabling it. The condition stops matching at HTTPS apex, preventing a self-redirect, and does not match app/API subdomains.

Expected examples:

| Request | First destination |
| --- | --- |
| `http://unifydesk.in/` | `https://unifydesk.in/` |
| `http://unifydesk.in/email-campaigns?utm_source=qa&x=1` | `https://unifydesk.in/email-campaigns?utm_source=qa&x=1` |
| `https://www.unifydesk.in/sms-marketing?x=1` (when active) | `https://unifydesk.in/sms-marketing?x=1` |
| `http://www.unifydesk.in/product?x=1` (when active) | `https://unifydesk.in/product?x=1` |
| `https://unifydesk.in/product?x=1` | Served normally, no scheme/domain redirect |

## Alternative: Always Use HTTPS

If every hostname in the zone is intended to require HTTPS, open **SSL/TLS → Edge Certificates → Always Use HTTPS** and enable it. This setting affects all hosts/subdomains, so the scoped rule above is preferable when the requested change concerns only this website. Always Use HTTPS does not canonicalize `www` to apex: retain a separate `http.host eq "www.unifydesk.in"` redirect if `www` is active. Avoid conflicting or redundant redirect rules.

## Verify after enabling the edge rule and deploying the website

Run these from an environment with public network access. Quote URLs containing query parameters.

```bash
curl -sS -I 'http://unifydesk.in/'
curl -sS -I 'http://unifydesk.in/email-campaigns?utm_source=qa&x=1'
curl -sS -I 'https://unifydesk.in/email-campaigns?utm_source=qa&x=1'
curl -sS -L --max-redirs 5 -o /dev/null -w '%{http_code} %{url_effective} %{num_redirects}\n' 'http://unifydesk.in/email-campaigns?utm_source=qa&x=1'
```

Require a 301/308 response from HTTP, a Location with the same path and query, a successful HTTPS page, and no redirect loop. Repeat for `/`, `/product`, `/pricing` and all five new routes. If `www` is active, repeat both HTTP and HTTPS requests for it. Check an unknown path still ends in a real 404; do not replace the assets' `404-page` handling with an SPA fallback. Preserve any intentional path normalization independently of the scheme/domain rule.

Check HTTPS page titles, canonicals, H1s, image descriptions and JSON-LD on the deployed build. Submit the updated `https://unifydesk.in/sitemap.xml` in Google Search Console, then inspect the five new HTTPS URLs. Record live-test and indexed-canonical results separately: a valid static export does not prove a URL is deployed or indexed. Re-run the rendered crawler and classify the intentionally empty adjacent brand marks rather than adding redundant text to silence its image count.

## Primary references checked 7 October 2026

- [Cloudflare Workers assets redirects](https://developers.cloudflare.com/workers/static-assets/redirects/): source restrictions and supported destinations.
- [Cloudflare Single Redirect settings](https://developers.cloudflare.com/rules/url-forwarding/single-redirects/settings/): dynamic targets, permanent status and query preservation.
- [Cloudflare Always Use HTTPS](https://developers.cloudflare.com/ssl/edge-certificates/additional-options/always-use-https/): zone-wide scope and dashboard steps.
- [Google sitemap guidance](https://developers.google.com/search/docs/crawling-indexing/sitemaps/build-sitemap): accurate significant-update dates; priority/changefreq are ignored by Google.
