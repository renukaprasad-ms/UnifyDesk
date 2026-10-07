import type {Metadata} from "next";
import {Cloud,Mail,MessageCircle,Send,Target} from "lucide-react";
import {Footer,Header} from "../components";
import Link from "next/link";
import {ChannelLinks} from "../channel-sections";

export const metadata:Metadata={
  title:"Integrations for WhatsApp, Email, SMS & Ads",
  description:"Explore UnifyDesk integrations for WhatsApp, Gmail, Microsoft, Amazon SES, SMS providers, Google Ads and Meta Ads.",
  alternates:{canonical:"/integrations"}
};

const groups=[
  ["Messaging","WhatsApp, SMS and channel delivery providers.",MessageCircle,[
    ["WhatsApp","In development","Connect a company-owned business account and number for customer conversations and provider-aware outreach. Number readiness, account validation and applicable template rules remain prerequisites.","/whatsapp-shared-inbox","WhatsApp team conversations"],
    ["MSG91","In development","The current managed SMS provider direction. Prepare the sender and required templates, review destination requirements and confirm provisioning before treating SMS delivery as ready.","/sms-marketing","Managed SMS workflow"],
    ["MetaReach","Evaluation","An additional messaging provider under evaluation. This is an exploration status, with no committed public launch date or promise of production availability.","/contact","Follow launch updates"]
  ]],
  ["Email","Inbox connections and managed sending infrastructure.",Mail,[
    ["Gmail","In development","A mailbox connection for email conversations, inbox synchronization and replies. Its role is distinct from managed campaign delivery; connecting Gmail is not a promise of bulk sending.","/omnichannel-inbox","Connected inbox workflow"],
    ["Microsoft","In development","Connect Microsoft mailbox conversations to the customer workspace. Mailbox authorization and provider permissions apply. This inbox connection is separate from campaign sending infrastructure.","/omnichannel-inbox","Email and messaging context"],
    ["Amazon SES","In development","The current managed email direction for campaign delivery. Sender or domain verification, provider readiness and capacity matter before sending. Email usage is a separate billing layer.","/email-campaigns","Managed email campaigns"],
    ["SendGrid","Planned","An additional campaign delivery provider choice planned for public rollout. Provider setup and real delivery validation are still required; this label does not announce a live public integration.","/email-campaigns","Campaign sending preparation"],
    ["Azure Email","Planned","An additional email delivery provider choice planned for public rollout. Sender setup, provider authorization and delivery readiness must be confirmed before a company relies on this connection.","/email-campaigns","Email provider readiness"]
  ]],
  ["Advertising","Provider-native campaign workflows through one Ad Center.",Target,[
    ["Google Ads","In development","Bring connected advertising account and campaign work into the Ad Center direction. Provider account permissions and supported capabilities apply; media spend stays with Google.","/product#product-tour","Explore the Ad Center tour"],
    ["Meta Ads","In development","Organize connected Meta advertising account and campaign work in the same workspace direction. Provider permissions remain necessary and Meta media spend is separate from platform access.","/pricing","Understand separate media spend"]
  ]]
] as const;

export default function Integrations(){
  return <><Header/><main id="main-content" className="routePage">
    <section className="routeHero"><div className="statusBadge"><span/>INTEGRATIONS</div><h1>Keep the providers. Simplify the operating layer.</h1><p>UnifyDesk connects WhatsApp, email, SMS and advertising providers so teams can work from one customer communication workspace without replacing the underlying services.</p></section>
    <section className="routeSection soft integrationRoute">
      <p className="seoSectionIntro">Public access is coming soon. Labels describe the public rollout direction, not a guarantee that a configured account can send or synchronize today. Each provider has its own authorization, setup and readiness requirements.</p>
      {groups.map(([title,description,Icon,providers])=><article className="integrationGroup reveal" key={title}><div className="integrationGroupHead"><div><div className="featureIcon"><Icon size={21}/></div><h2>{title}</h2><p>{description}</p></div><span className="softBadge">PROVIDER LAYER</span></div><div className="providerRouteGrid seoProviderGrid">{providers.map(([name,status,body,href,label])=><div key={name}><b>{name}</b><small className={status==="In development"?"building":""}>{status}</small><p>{body}</p><Link href={href}>{label}</Link></div>)}</div></article>)}
    </section>
    <section className="providerPrinciple"><Cloud size={28}/><p className="darkKicker">HOW UNIFYDESK CONNECTS</p><h2>One workspace above the providers you already trust.</h2><p>The integration layer keeps provider-specific capabilities visible while giving teams a connected direction for conversations, broadcasts and campaign operations. Company-authorized connections should be ready and validated before they are relied on for customer work. Managed email and SMS usage and external advertising spend remain separate from platform access.</p><Link className="whiteButton" href="/pricing">Review the billing model</Link></section>
    <section className="splitRouteSection"><div className="routeCopy"><p className="kicker">PLAN YOUR SETUP</p><h2>Choose the provider for the job.</h2><p>Start with the customer workflow: inbox conversations, outbound campaigns or advertising. Then confirm the account your company controls, the permissions needed and the provider's requirements. A mailbox and a campaign sender serve different purposes.</p></div><div className="seoPreparation"><h3>Three checks before connecting</h3><ul><li>Confirm account ownership and an authorized company member.</li><li>Review verification, sender, template and provider limits.</li><li>Check availability, workspace entitlements and usage costs.</li></ul><Link className="textLink" href="/security">Review provider credentials and access</Link></div></section>
    <ChannelLinks paths={["/whatsapp-broadcast","/sms-marketing","/email-campaigns","/omnichannel-inbox"]}/>
  </main><Footer/></>
}
