import Link from "next/link";
import { ArrowRight, ChevronRight } from "lucide-react";
import { guideFor } from "./seo-content";

export function ChannelLinks({ paths, title = "Explore the workflow for your channel.", intro }: { paths: string[]; title?: string; intro?: string }) {
  return <section className="routeSection seoRelated">
    <p className="kicker">CHANNEL WORKFLOWS</p>
    <h2 className="sectionTitle">{title}</h2>
    {intro && <p className="seoSectionIntro">{intro}</p>}
    <div className="seoLinkGrid">{paths.map(path => {
      const guide = guideFor(path);
      return <Link className="seoGuideLink" href={path} key={path}><div><h3>{guide.label}</h3><p>{guide.purpose}</p></div><ArrowRight size={20} aria-hidden="true" /></Link>;
    })}</div>
  </section>;
}

export function ContentFaq({ title, items }: { title: string; items: { title: string; body: string }[] }) {
  return <section className="routeSection soft seoFaq"><div><p className="kicker">PRACTICAL QUESTIONS</p><h2 className="sectionTitle">{title}</h2></div><div className="faqList">{items.map(item => <details key={item.title}><summary>{item.title}<ChevronRight size={18} aria-hidden="true" /></summary><p>{item.body}</p></details>)}</div></section>;
}
