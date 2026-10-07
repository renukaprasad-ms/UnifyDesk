import type {Metadata} from "next";
import {Cloud,Mail,MessageCircle,Send,Target} from "lucide-react";
import {Footer,Header} from "../components";

export const metadata:Metadata={
  title:"Integrations for WhatsApp, Email, SMS & Ads",
  description:"Explore UnifyDesk integrations for WhatsApp, Gmail, Microsoft, Amazon SES, SMS providers, Google Ads and Meta Ads.",
  alternates:{canonical:"/integrations"}
};

const groups=[
  ["Messaging","WhatsApp, SMS and channel delivery providers.",MessageCircle,[["WhatsApp","In development"],["MSG91","In development"],["MetaReach","Evaluation"]]],
  ["Email","Inbox connections and managed sending infrastructure.",Mail,[["Gmail","In development"],["Microsoft","In development"],["Amazon SES","In development"],["SendGrid","Planned"],["Azure Email","Planned"]]],
  ["Advertising","Provider-native campaign workflows through one Ad Center.",Target,[["Google Ads","In development"],["Meta Ads","In development"]]]
] as const;

export default function Integrations(){
  return <><Header/><main id="main-content" className="routePage">
    <section className="routeHero"><div className="statusBadge"><span/>INTEGRATIONS</div><h1>Keep the providers. Simplify the operating layer.</h1><p>UnifyDesk connects WhatsApp, email, SMS and advertising providers so teams can work from one customer communication workspace without replacing the underlying services.</p></section>
    <section className="routeSection soft integrationRoute">
      {groups.map(([title,description,Icon,providers])=><article className="integrationGroup reveal" key={title}><div className="integrationGroupHead"><div><div className="featureIcon"><Icon size={21}/></div><h2>{title}</h2><p>{description}</p></div><span className="softBadge">PROVIDER LAYER</span></div><div className="providerRouteGrid">{providers.map(([name,status])=><div key={name}><b>{name}</b><small className={status==="In development"?"building":""}>{status}</small></div>)}</div></article>)}
    </section>
    <section className="providerPrinciple"><Cloud size={28}/><p className="darkKicker">HOW UNIFYDESK CONNECTS</p><h2>One workspace above the providers you already trust.</h2><p>Provider fees, delivery costs and advertising spend remain separate from the UnifyDesk subscription. The integration layer keeps provider-specific capabilities available while giving teams a consistent workflow for conversations, broadcasts and campaign operations.</p></section>
  </main><Footer/></>
}
