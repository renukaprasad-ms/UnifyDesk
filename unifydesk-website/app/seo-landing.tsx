import Link from "next/link";
import { ArrowRight, Check, MessageSquare } from "lucide-react";
import { Footer, Header } from "./components";
import { ChannelLinks, ContentFaq } from "./channel-sections";
import { guideFor } from "./seo-content";

export default function SeoLanding({ path }: { path: string }) {
  const guide = guideFor(path);
  const url = `https://unifydesk.in${path}`;
  const schema = {
    "@context": "https://schema.org",
    "@graph": [
      { "@type": "WebPage", "@id": `${url}#webpage`, url, name: guide.title, description: guide.description, isPartOf: { "@id": "https://unifydesk.in/#website" }, breadcrumb: { "@id": `${url}#breadcrumb` } },
      { "@type": "BreadcrumbList", "@id": `${url}#breadcrumb`, itemListElement: [{ "@type": "ListItem", position: 1, name: "Home", item: "https://unifydesk.in" }, { "@type": "ListItem", position: 2, name: guide.label, item: url }] },
    ],
  };
  return <><Header /><main id="main-content" className="routePage">
    <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(schema).replace(/</g, "\\u003c") }} />
    <section className="routeHero seoHero">
      <div className="statusBadge"><span />{guide.label}</div>
      <h1>{guide.heading}</h1><p>{guide.intro}</p>
      <div className="seoHeroActions"><Link className="button" href="/product#product-tour">Explore the product tour <ArrowRight size={17} aria-hidden="true" /></Link><Link className="textLink" href="/integrations">Check integration status <ArrowRight size={16} aria-hidden="true" /></Link></div>
      <small className="seoLaunchNote">In development · Public access coming soon · Tour uses sample data</small>
    </section>
    <section className="routeSection">
      <p className="kicker">THE TEAM WORKFLOW</p><h2 className="sectionTitle">{guide.workflowTitle}</h2><p className="seoSectionIntro">{guide.purpose}</p>
      <ol className="seoSteps">{guide.steps.map((step, i) => <li className="reveal" key={step.title}><span className="seoStepNumber" aria-hidden="true">0{i + 1}</span><h3>{step.title}</h3><p>{step.body}</p></li>)}</ol>
    </section>
    <section className="routeSection soft"><p className="kicker">WHAT TO CONSIDER</p><h2 className="sectionTitle">Keep the customer, channel and team connected.</h2><div className="routeCardGrid three">{guide.features.map(item => <article className="routeCard reveal" key={item.title}><div className="featureIcon"><MessageSquare size={21} aria-hidden="true" /></div><h3>{item.title}</h3><p>{item.body}</p></article>)}</div><p className="seoInlineLinks">Review <Link href="/security">company access and account protection</Link> and the <Link href="/pricing">platform and usage pricing model</Link> as you plan your setup.</p></section>
    <section className="splitRouteSection seoProvider"><div className="routeCopy"><p className="kicker">PROVIDERS & READINESS</p><h2>{guide.providerTitle}</h2><p>{guide.providerBody}</p><Link className="textLink" href="/integrations">Explore integrations <ArrowRight size={16} aria-hidden="true" /></Link></div><div className="seoPreparation"><h3>Prepare before connecting</h3><ul>{guide.preparation.map(item => <li key={item}><Check size={18} aria-hidden="true" /><span>{item}</span></li>)}</ul><Link href="/solutions" className="textLink">Find your team's workflow <ArrowRight size={16} aria-hidden="true" /></Link></div></section>
    <section className="routeSection soft"><p className="kicker">EXAMPLE USE CASES</p><h2 className="sectionTitle">Put the workflow in context.</h2><div className="routeCardGrid seoTwoColumns">{guide.useCases.map(item => <article className="routeCard" key={item.title}><h3>{item.title}</h3><p>{item.body}</p></article>)}</div></section>
    <ContentFaq title={`Questions about ${guide.label}.`} items={guide.faqs} />
    <ChannelLinks paths={guide.related} title="Continue exploring connected customer work." />
    <section className="centerRouteCta"><p className="kicker">SEE THE WORKSPACE</p><h2>Explore how the work comes together.</h2><p>The product tour shows the developing experience with illustrative data. Public sign-up and final pricing are coming soon.</p><Link className="button" href="/product#product-tour">Take the product tour <ArrowRight size={17} aria-hidden="true" /></Link></section>
  </main><Footer /></>;
}
