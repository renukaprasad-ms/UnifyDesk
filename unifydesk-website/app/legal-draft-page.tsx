import Link from "next/link";
import { Header, Footer } from "./components";

export type LegalSection = { title: string; text: string[]; points?: string[] };
export type RelatedPage = { label: string; href: string };
export default function LegalDraftPage({ title, intro, sections, related = [] }: {
  title: string; intro: string; sections: LegalSection[]; related?: RelatedPage[];
}) {
  return <><Header/><main id="main-content" className="legal">
    <p className="kicker">LEGAL · REVIEW DRAFT — NOT EFFECTIVE</p>
    <h1>{title}</h1><p className="legalUpdated">Prepared 12 October 2026 · Awaiting legal and operational approval</p>
    <p className="legalIntro">{intro}</p>
    <aside>This is a working draft, not a final legal notice or binding agreement.
      Verified legal identity, public contact details and remaining backend checks are required before publication.</aside>
    {sections.map(s=><section key={s.title}><h2>{s.title}</h2>
      {s.text.map((t,i)=><p key={i}>{t}</p>)}
      {s.points && <ul>{s.points.map(t=><li key={t}>{t}</li>)}</ul>}
    </section>)}
    {related.length>0 && <section><h2>Related documents</h2><ul>{related.map(r=><li key={r.href}><Link href={r.href}>{r.label}</Link></li>)}</ul></section>}
  </main><Footer/></>;
}
