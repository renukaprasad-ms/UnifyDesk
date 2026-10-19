import fs from "node:fs/promises";
import path from "node:path";

const origin = "https://unifydesk.in";
const guideRoutes = ["/guides", "/guides/whatsapp-broadcast-india", "/guides/email-broadcast-vs-email-marketing", "/guides/bulk-sms-dlt-india"];
const channelRoutes = ["/best-omnichannel-broadcast-india", "/whatsapp-shared-inbox", "/whatsapp-broadcast", "/sms-marketing", "/email-campaigns", "/omnichannel-inbox"];
const routes = ["/", "/product", "/solutions", "/integrations", "/pricing", "/security", "/about", "/contact", ...channelRoutes, ...guideRoutes];
const draftLegalRoutes = ["/privacy", "/terms", "/data-deletion", "/cookies", "/subprocessors", "/acceptable-use", "/refund-policy", "/dpa"];
const preservedTitles = {
  "/": "UnifyDesk | Customer Communication & Marketing Platform",
  "/product": "Customer Communication Platform | UnifyDesk",
  "/solutions": "Customer Communication & Marketing Solutions | UnifyDesk",
  "/integrations": "Integrations for WhatsApp, Email, SMS & Ads | UnifyDesk",
  "/pricing": "Pricing for Customer Communication Teams | UnifyDesk",
  "/security": "Security & Data Protection | UnifyDesk",
};
const failures = [];
const pages = [];
const titles = new Set();
const descriptions = new Set();
const documents = new Map();
const decode = (value) => value.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#x27;|&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">");
const attributes = (tag) => Object.fromEntries([...tag.matchAll(/([\w:-]+)="([^"]*)"/g)].map(([, key, value]) => [key, decode(value)]));
const fail = (route, issue) => failures.push(`${route}: ${issue}`);
const exportedPath = (route) => path.join("out", route === "/" ? "index.html" : `${route.slice(1)}.html`);

for (const route of routes) documents.set(route, await fs.readFile(exportedPath(route), "utf8"));
for (const route of draftLegalRoutes) {
  try { documents.set(route, await fs.readFile(exportedPath(route), "utf8")); }
  catch { fail(route, "missing exported draft legal page"); }
}
const sitemap = await fs.readFile("out/sitemap.xml", "utf8");
const robots = await fs.readFile("out/robots.txt", "utf8");
if (!robots.includes(`${origin}/sitemap.xml`)) fail("robots.txt", "missing sitemap reference");
if ((sitemap.match(/<url>/g) ?? []).length !== routes.length) fail("sitemap.xml", "unexpected route count");

for (const [route, html] of documents) {
  if (!routes.includes(route)) continue;
  const title = decode(html.match(/<title>([\s\S]*?)<\/title>/)?.[1] ?? "");
  const metas = [...html.matchAll(/<meta\b[^>]*>/g)].map(([tag]) => attributes(tag));
  const description = metas.find(meta => meta.name === "description")?.content ?? "";
  const canonical = [...html.matchAll(/<link\b[^>]*>/g)].map(([tag]) => attributes(tag)).filter(link => link.rel === "canonical");
  const h1s = [...html.matchAll(/<h1\b[^>]*>([\s\S]*?)<\/h1>/g)];
  const main = html.match(/<main\b[^>]*>([\s\S]*?)<\/main>/)?.[1] ?? "";
  const copy = decode(main.replace(/<script\b[\s\S]*?<\/script>/g, " ").replace(/<[^>]+>/g, " ")).replace(/\s+/g, " ").trim();
  const imgs = [...html.matchAll(/<img\b[^>]*>/g)].map(([tag]) => attributes(tag));
  const schemas = [...html.matchAll(/<script\b[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)].flatMap(([, json]) => {
    try { const parsed = JSON.parse(json); return Array.isArray(parsed) ? parsed : parsed["@graph"] ?? [parsed]; }
    catch { fail(route, "invalid JSON-LD"); return []; }
  });
  if (!title || titles.has(title)) fail(route, "missing or duplicate title");
  if (!description || descriptions.has(description)) fail(route, "missing or duplicate description");
  titles.add(title); descriptions.add(description);
  if (preservedTitles[route] && title !== preservedTitles[route]) fail(route, "established title changed");
  if (h1s.length !== 1) fail(route, `expected one H1, found ${h1s.length}`);
  const expectedCanonical = `${origin}${route === "/" ? "" : route}`;
  if (canonical.length !== 1 || canonical[0].href.replace(/\/$/, "") !== expectedCanonical) fail(route, "incorrect canonical");
  if (metas.some(meta => meta.name === "robots" && /noindex|nofollow/.test(meta.content))) fail(route, "non-indexable robots metadata");
  if (!sitemap.includes(`<loc>${origin}${route === "/" ? "" : route}</loc>`)) fail(route, "absent from sitemap");
  if (!schemas.some(schema => schema["@type"] === "Organization") || !schemas.some(schema => schema["@type"] === "WebSite")) fail(route, "missing global schema");
  if (channelRoutes.includes(route) || guideRoutes.includes(route)) {
    if (!schemas.some(schema => schema["@type"] === "WebPage" && schema.url === `${origin}${route}`)) fail(route, "missing route-specific WebPage");
    if (!schemas.some(schema => schema["@type"] === "BreadcrumbList")) fail(route, "missing breadcrumb schema");
    if (metas.find(meta => meta.property === "og:url")?.content !== `${origin}${route}`) fail(route, "incorrect social URL");
    if (route.startsWith("/guides/") && !schemas.some(schema => schema["@type"] === "Article")) fail(route, "missing Article schema");
  }
  for (const image of imgs) {
    if (!("alt" in image)) fail(route, `image missing alt attribute: ${image.src}`);
    // Brand marks are redundant with the adjacent visible UnifyDesk name.
    if (!image.alt && image.src !== "/logo-icon.svg") fail(route, `unclassified empty image alt: ${image.src}`);
  }
  let internalLinks = 0;
  for (const [tag] of html.matchAll(/<a\b[^>]*>/g)) {
    const href = attributes(tag).href;
    if (!href?.startsWith("/") || href.startsWith("//")) continue;
    internalLinks++;
    const target = new URL(href, origin);
    const targetRoute = target.pathname.replace(/\/$/, "") || "/";
    if (!documents.has(targetRoute)) { fail(route, `unknown internal route ${href}`); continue; }
    if (target.hash && !documents.get(targetRoute).includes(`id="${decodeURIComponent(target.hash.slice(1))}"`)) fail(route, `missing fragment ${href}`);
  }
  pages.push({ route, title, description, canonical: canonical[0]?.href, h1: decode(h1s[0]?.[1].replace(/<[^>]+>/g, "") ?? ""), mainWords: copy ? copy.split(" ").length : 0, images: imgs.length, intentionallyEmptyBrandAlt: imgs.filter(img => img.src === "/logo-icon.svg" && img.alt === "").length, internalLinks, schemaTypes: schemas.map(schema => schema["@type"]) });
}
for (const route of draftLegalRoutes) {
  const html = documents.get(route);
  if (!html) continue;
  const metas = [...html.matchAll(/<meta\b[^>]*>/g)].map(([tag]) => attributes(tag));
  const robotsMeta = metas.find(meta => meta.name === "robots")?.content ?? "";
  if (!/noindex/i.test(robotsMeta)) fail(route, "unapproved legal draft must be noindex");
  if (sitemap.includes(`<loc>${origin}${route}</loc>`)) fail(route, "draft legal page must not appear in sitemap");
  if (!html.includes("REVIEW DRAFT")) fail(route, "missing visible legal draft warning");
}
for (const file of ["public/_redirects", "out/_redirects"]) {
  try { const redirects = await fs.readFile(file, "utf8"); if (/^https?:\/\//m.test(redirects)) fail(file, "unsupported domain-level source rule"); }
  catch (error) { if (error.code !== "ENOENT") throw error; }
}
const report = { checkedAt: new Date().toISOString(), source: "Local production static export; no live deployment or Google Search Console verification", routeCount: pages.length, passed: failures.length === 0, pages, failures };
const reportIndex = process.argv.indexOf("--report");
if (reportIndex !== -1) {
  const destination = process.argv[reportIndex + 1];
  if (!destination) throw new Error("--report requires a destination");
  await fs.mkdir(path.dirname(destination), { recursive: true });
  await fs.writeFile(destination, JSON.stringify(report, null, 2) + "\n");
}
console.log(JSON.stringify({ passed: report.passed, routeCount: pages.length, failures, pages: pages.map(({route, mainWords, images, intentionallyEmptyBrandAlt}) => ({route, mainWords, images, intentionallyEmptyBrandAlt})) }, null, 2));
if (failures.length) process.exitCode = 1;
