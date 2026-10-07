import type {Metadata} from "next";
import {BriefcaseBusiness,Building2,Check,Headphones,Megaphone} from "lucide-react";
import {Footer,Header} from "../components";

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
  return <><Header/><main className="routePage">
    <section className="routeHero"><div className="statusBadge"><span/>SOLUTIONS</div><h1>Different teams. One customer context.</h1><p>Give business, support and marketing teams shared inbox, customer context, broadcast and advertising workflows while keeping customer work connected across the company.</p></section>
    <section className="routeSection soft"><h2 className="sectionTitle">Designed around the teams doing the work.</h2><div className="routeCardGrid four">{teams.map(([title,tag,description,Icon,points],i)=><article className={"routeCard reveal delay"+((i%3)+1)} key={title}><div className="featureIcon"><Icon size={21}/></div><span className="softBadge">{tag}</span><h3>{title}</h3><p>{description}</p><div className="pointList">{points.map(p=><span key={p}><Check size={15}/>{p}</span>)}</div></article>)}</div></section>
    <section className="journeySection"><p className="kicker">ONE CUSTOMER JOURNEY</p><h2 className="sectionTitle">Support, marketing and growth should not operate from isolated customer records.</h2><div className="journeyFlow">{[["01","Conversation"],["02","Customer context"],["03","Audience"],["04","Broadcast"],["05","Paid campaign"],["06","Performance"]].map(([n,t])=><div className="reveal" key={n}><span>{n}</span><b>{t}</b></div>)}</div></section>
  </main><Footer/></>
}