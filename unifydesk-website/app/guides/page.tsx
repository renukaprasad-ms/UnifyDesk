import type { Metadata } from "next";
import Link from "next/link";
import { ArrowRight } from "lucide-react";
import { Footer, Header } from "../components";
import { guides, guidePath } from "../guide-content";

export const metadata: Metadata = {
  title: "Messaging & Email Marketing Guides for Businesses",
  description: "Practical UnifyDesk guides to WhatsApp broadcasts, email campaign sending, and DLT-ready bulk SMS in India. Learn the steps and provider prerequisites.",
  alternates: { canonical: "/guides" },
  openGraph: { title: "Messaging & Email Marketing Guides for Businesses | UnifyDesk", description: "Practical guides for WhatsApp broadcasts, email marketing and bulk SMS in India.", url: "/guides", type: "website" },
};

const url = "https://unifydesk.in/guides";
const schema = { "@context": "https://schema.org", "@graph": [
  { "@type": "WebPage", url, name: "Business messaging and email marketing guides", isPartOf: { "@id": "https://unifydesk.in/#website" }, breadcrumb: { "@id": `${url}#breadcrumb` } },
  { "@type": "BreadcrumbList", "@id": `${url}#breadcrumb`, itemListElement: [
    { "@type": "ListItem", position: 1, name: "Home", item: "https://unifydesk.in" },
    { "@type": "ListItem", position: 2, name: "Guides", item: url },
  ] },
] };

export default function Guides() {
  return <><Header /><main id="main-content" className="routePage">
    <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(schema).replace(/</g, "\\u003c") }} />
    <section className="routeHero seoHero"><p className="kicker">LEARNING RESOURCES</p>
      <h1>Practical guides to business messaging and email campaigns.</h1>
      <p>Learn how to prepare opted-in WhatsApp broadcasts, email marketing campaigns and bulk SMS in India. These guides focus on requirements and responsible sending, not shortcuts.</p>
      <small className="seoLaunchNote">UnifyDesk is in development · Public access coming soon</small>
    </section>
    <section className="routeSection"><p className="kicker">EXPLORE BY CHANNEL</p><h2 className="sectionTitle">Start with the customer communication you want to improve.</h2>
      <div className="routeCardGrid seoTwoColumns">
        {guides.map(item => <article className="routeCard" key={item.slug}>
          <p className="kicker">{item.label}</p><h3><Link href={guidePath(item.slug)}>{item.title}</Link></h3>
          <p>{item.description}</p><Link className="textLink" href={guidePath(item.slug)}>Read the guide <ArrowRight size={16} aria-hidden="true" /></Link>
        </article>)}
      </div>
    </section>
    <section className="centerRouteCta"><h2>See the developing UnifyDesk workspace.</h2><p>Explore how contacts, inboxes and campaign preparation can work together. The tour uses illustrative sample data.</p><Link className="button" href="/product#product-tour">Explore the product tour <ArrowRight size={17} aria-hidden="true" /></Link></section>
  </main><Footer /></>;
}
