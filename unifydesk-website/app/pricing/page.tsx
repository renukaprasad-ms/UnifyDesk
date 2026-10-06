import type {Metadata} from "next";
import {Check} from "lucide-react";
import {Footer,Header} from "../components";

export const metadata:Metadata={
  title:"Pricing | UnifyDesk",
  description:"Explore the UnifyDesk plan structure for workspace access, managed services and connected providers.",
  alternates:{canonical:"/pricing"}
};

const plans=[
  ["Free","Explore the core workspace and validate your setup.","₹0",["Core workspace","Starter user limits","Basic connections"],false],
  ["Starter","For small teams centralizing customer communication.","Pricing coming soon",["Everything in Free","Higher workspace limits","Company controls"],false],
  ["Growth","For teams running communication and campaigns together.","Pricing coming soon",["Broadcast workflows","Ad Center workflows","Expanded team controls"],true]
] as const;

export default function Pricing(){
  return <><Header/><main className="routePage">
    <section className="routeHero"><div className="statusBadge"><span/>PRICING</div><h1>Plans for the workspace. Provider usage stays separate.</h1><p>Public pricing is being finalized. The plan structure separates UnifyDesk platform access from managed email, SMS and third-party advertising spend.</p></section>
    <section className="routeSection"><div className="pricingRouteGrid">{plans.map(([name,description,price,items,featured])=><article className={(featured?"featured ":"")+"routePricingCard reveal"} key={name}>{featured&&<span className="popular">Recommended</span>}<h2>{name}</h2><p>{description}</p><strong>{price}</strong><div className="pointList">{items.map(x=><span key={x}><Check size={15}/>{x}</span>)}</div><span className={(featured?"button":"outlineButton")+" disabledCta"}>Coming soon</span></article>)}</div></section>
    <section className="routeSection soft"><p className="kicker">BILLING MODEL</p><h2 className="sectionTitle">Two layers keep billing understandable.</h2><div className="routeCardGrid three">{[["Platform subscription","Users, workspace limits, feature access and company-level entitlements."],["Managed services","Email and SMS provider usage billed separately based on actual sending and provider costs."],["Advertising spend","Google and Meta media spend stays with the connected advertising providers."]].map(([t,b])=><article className="routeCard reveal" key={t}><h3>{t}</h3><p>{b}</p></article>)}</div></section>
  </main><Footer/></>
}