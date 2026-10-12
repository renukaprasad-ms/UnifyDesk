import Link from "next/link";
import { ArrowRight, CheckCircle2 } from "lucide-react";
import { Footer, Header } from "./components";
import { guideForSlug, guidePath, guides } from "./guide-content";

export default function GuideLanding({ slug }: { slug: string }) {
  const guide = guideForSlug(slug);
  const url = `https://unifydesk.in${guidePath(slug)}`;
  const schema = {
    "@context": "https://schema.org",
    "@graph": [
      { "@type": "WebPage", "@id": `${url}#webpage`, url, name: guide.title, description: guide.description, isPartOf: { "@id": "https://unifydesk.in/#website" }, breadcrumb: { "@id": `${url}#breadcrumb` } },
      { "@type": "BreadcrumbList", "@id": `${url}#breadcrumb`, itemListElement: [
        { "@type": "ListItem", position: 1, name: "Home", item: "https://unifydesk.in" },
        { "@type": "ListItem", position: 2, name: "Guides", item: "https://unifydesk.in/guides" },
        { "@type": "ListItem", position: 3, name: guide.label, item: url },
      ] },
      { "@type": "Article", "@id": `${url}#article`, headline: guide.title, description: guide.description, datePublished: guide.published, dateModified: guide.published,
        author: { "@id": "https://unifydesk.in/#organization" }, publisher: { "@id": "https://unifydesk.in/#organization" },
        mainEntityOfPage: { "@id": `${url}#webpage` } },
    ],
  };
  return <><Header /><main id="main-content" className="routePage">
    <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(schema).replace(/</g, "\\u003c") }} />
    <article>
      <header className="routeHero seoHero">
        <p className="kicker"><Link href="/guides">Resources</Link> / {guide.label}</p>
        <h1>{guide.heading}</h1>
        <p>{guide.intro}</p>
        <p className="guideByline">UnifyDesk guides · Published October 2026 · Educational content</p>
      </header>
      <div className="guideLayout">
        <div className="guideArticle">
          {guide.sections.map((section, index) => <section className="guideArticleSection" key={section.heading}>
            <h2>{section.heading}</h2>
            {section.paragraphs.map(paragraph => <p key={paragraph}>{paragraph}</p>)}
            {section.bullets && <ul className="guideChecklist">{section.bullets.map(bullet => <li key={bullet}><CheckCircle2 size={18} aria-hidden="true" />{bullet}</li>)}</ul>}
            {index === 1 && <p className="guideContextLink">Related product workflow: <Link href={guide.primaryPath}>Explore {guide.label.toLowerCase()} in UnifyDesk <ArrowRight size={15} aria-hidden="true" /></Link></p>}
          </section>)}
          <section className="guideArticleSection">
            <h2>Frequently asked questions</h2>
            {guide.faqs.map(item => <div className="guideFaq" key={item.question}><h3>{item.question}</h3><p>{item.answer}</p></div>)}
          </section>
        </div>
        <aside className="guideAside" aria-label="Related UnifyDesk resources">
          <p className="kicker">Explore UnifyDesk</p>
          <h2>Plan the workflow, then explore the product.</h2>
          <p>UnifyDesk is in development. Public access coming soon. The product tour uses sample data, not live campaigns.</p>
          <Link className="textLink" href={guide.primaryPath}>Explore the related feature <ArrowRight size={16} aria-hidden="true" /></Link>
          <Link className="textLink" href="/product#product-tour">View sample product tour <ArrowRight size={16} aria-hidden="true" /></Link>
          <Link className="textLink" href="/integrations">Check integration status <ArrowRight size={16} aria-hidden="true" /></Link>
        </aside>
      </div>
    </article>
    <section className="routeSection soft"><p className="kicker">KEEP EXPLORING</p><h2 className="sectionTitle">Related guides and workflows</h2>
      <div className="routeCardGrid seoTwoColumns">
        {guides.filter(item => item.slug !== slug).map(item => <article className="routeCard" key={item.slug}><h3><Link href={guidePath(item.slug)}>{item.title}</Link></h3><p>{item.description}</p><Link className="textLink" href={guidePath(item.slug)}>Read the guide <ArrowRight size={15} aria-hidden="true" /></Link></article>)}
      </div>
    </section>
  </main><Footer /></>;
}
