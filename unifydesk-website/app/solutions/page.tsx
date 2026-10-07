import type {Metadata} from "next";
import {BriefcaseBusiness,Building2,Check,Headphones,Megaphone} from "lucide-react";
import {Footer,Header} from "../components";
import Link from "next/link";
import {ChannelLinks} from "../channel-sections";

export const metadata:Metadata={
  title:"Customer Communication & Marketing Solutions",
  description:"Unify customer support, marketing, shared inbox, broadcast and advertising workflows around one connected customer context.",
  alternates:{canonical:"/solutions"}
};

const teams=[
  ["Business owners","VISIBILITY","See customer communication and growth workflows without stitching dashboards together.",Building2,["Company-level visibility","Connected activity","Plan and access controls"]],
  ["Customer support","CONVERSATIONS","Give the team a shared place to work through conversations with context.",Headphones,["Shared inbox","Customer profile context","Channel workflows"]],
  ["Marketing teams","CAMPAIGNS","Move between broadcasts and advertising without losing audience context.",Megaphone,["Broadcast workflows","Ad Center","Performance context"]],
  ["Growing teams","CONTROL","Organize workspace access and providers as the team grows.",BriefcaseBusiness,["Roles & permissions","Provider connections","Company controls"]]
] as const;

export default function Solutions(){
  return <><Header/><main id="main-content" className="routePage">
    <section className="routeHero"><div className="statusBadge"><span/>SOLUTIONS</div><h1>Different teams. One customer context.</h1><p>Give business, support and marketing teams shared inbox, customer context, broadcast and advertising workflows while keeping customer work connected across the company.</p></section>
    <section className="routeSection soft"><h2 className="sectionTitle">Designed around the teams doing the work.</h2><div className="routeCardGrid four">{teams.map(([title,tag,description,Icon,points],i)=><article className={"routeCard reveal delay"+((i%3)+1)} key={title}><div className="featureIcon"><Icon size={21}/></div><span className="softBadge">{tag}</span><h3>{title}</h3><p>{description}</p><div className="pointList">{points.map(p=><span key={p}><Check size={15}/>{p}</span>)}</div></article>)}</div></section>
    <section className="routeSection"><p className="kicker">PUT IT INTO PRACTICE</p><h2 className="sectionTitle">Choose the work your team needs to connect.</h2><p className="seoSectionIntro">UnifyDesk is in development. These examples explain the intended workflows so you can evaluate the workspace, provider prerequisites and team controls before public access opens.</p><div className="routeCardGrid seoTwoColumns">
      <article className="routeCard"><h3>Answer a customer with the earlier conversation in view.</h3><p>A customer asks about an order on WhatsApp after emailing the company. Support can review available contact and conversation context before answering. The team still needs a clear process for follow-up and an authorized, ready provider connection.</p><Link className="textLink" href="/whatsapp-shared-inbox">Explore the WhatsApp shared inbox</Link><p>For enquiries moving between providers, review the <Link className="textLink" href="/omnichannel-inbox">omnichannel inbox workflow</Link>.</p></article>
      <article className="routeCard"><h3>Prepare outreach around a relevant customer group.</h3><p>Marketing starts with an audience, a purpose and recorded consent. Prepare the message and review provider requirements before sending. A contact list gives the team customer context; it does not automatically grant permission for a campaign.</p><Link className="textLink" href="/whatsapp-broadcast">Plan a WhatsApp broadcast</Link><p>Choose <Link className="textLink" href="/email-campaigns">email campaigns</Link> or <Link className="textLink" href="/sms-marketing">SMS marketing</Link> when those channels fit the audience and purpose.</p></article>
      <article className="routeCard"><h3>Give owners a clear view of the operating model.</h3><p>Business owners need to understand workspace access, provider setup and costs together. Review the platform entitlements the company needs, then account for managed messaging usage and advertising spend separately. Paid plan prices and final limits are coming soon.</p><Link className="textLink" href="/pricing">Understand platform and usage pricing</Link></article>
      <article className="routeCard"><h3>Organize access as the company grows.</h3><p>Decide who should manage provider connections and which product areas each role needs. Company membership and permissions govern access. Review account protection and session controls as colleagues join, change responsibilities or leave.</p><Link className="textLink" href="/security">Review company and account controls</Link><p>Confirm the current rollout stage on the <Link className="textLink" href="/integrations">integrations page</Link>.</p></article>
    </div></section>
    <section className="journeySection"><p className="kicker">ONE CUSTOMER JOURNEY</p><h2 className="sectionTitle">Support, marketing and growth should not operate from isolated customer records.</h2><div className="journeyFlow">{[["01","Conversation"],["02","Customer context"],["03","Audience"],["04","Broadcast"],["05","Paid campaign"],["06","Performance"]].map(([n,t])=><div className="reveal" key={n}><span>{n}</span><b>{t}</b></div>)}</div></section>
    <ChannelLinks paths={["/omnichannel-inbox","/email-campaigns"]} title="Start with the workflow that matters to your team."/>
  </main><Footer/></>
}
