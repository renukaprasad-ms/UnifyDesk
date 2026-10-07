import type {Metadata} from "next";
import Link from "next/link";
import {
  ArrowRight,BarChart3,Bell,Briefcase,Check,ChevronDown,FileText,Home as HomeIcon,
  Inbox,Layers3,LogOut,Mail,MessageCircle,Radio,Search,Send,ShieldCheck,
  Target,TrendingUp,UserRound,Users,Zap
} from "lucide-react";
import {Footer,Header} from "./components";

export const metadata:Metadata={
  title:{absolute:"UnifyDesk | Customer Communication & Marketing Platform"},
  description:"UnifyDesk is a customer communication and marketing platform for shared inboxes, contacts, broadcasts, email, SMS, WhatsApp, Google Ads and Meta Ads.",
  alternates:{canonical:"/"}
};

const features=[
  ["Unified Inbox","Work across connected customer channels from one shared workspace with customer context beside every conversation.",Inbox],
  ["Broadcast","Build audiences, preview recipients, schedule outreach and track delivery across supported channels.",Radio],
  ["Ad Center","Run Google and Meta advertising workflows without living inside separate provider dashboards.",Target],
  ["Customer context","Keep profiles, consent, activity and conversation history connected across workflows.",Users],
  ["Analytics","Review communication, campaign and operational activity from a clearer business view.",BarChart3],
  ["Email infrastructure","Use managed sending or supported email providers with delivery and domain controls.",Mail],
] as const;

const integrations=["WhatsApp","Gmail","Microsoft","Amazon SES","MSG91","Google Ads","Meta Ads"];

function WorkspacePreview(){
  const sidebar=[
    ["Dashboard",HomeIcon,true],["Notifications",Bell,false],["Contacts",Users,false],
    ["Broadcast",Radio,false],["Inbox",Inbox,false],["Ad Center",TrendingUp,false],
    ["Templates",FileText,false],["Company",Briefcase,false]
  ] as const;

  return <div className="workspacePreview reveal delay2">
    <div className="browserBar"><i/><i/><i/><span>app.unifydesk.in / workspace</span></div>
    <div className="workspaceShell">
      <aside className="workspaceSidebar">
        <div className="workspaceSwitcher">
          <div className="workspaceInitials">AS</div>
          <div><b>Acme Studio</b><small>Workspace</small></div>
          <ChevronDown size={14}/>
        </div>
        <small className="sidebarLabel">APP</small>
        <div className="workspaceNav">
          {sidebar.map(([label,Icon,active])=>
            <div className={"workspaceNavItem "+(active?"active":"")} key={label}>
              <Icon size={15}/><span>{label}</span>
              {["Broadcast","Inbox","Company"].includes(label)&&<ChevronDown className="workspaceChevron" size={13}/>}
            </div>
          )}
        </div>
        <small className="sidebarLabel">YOU</small>
        <div className="workspaceNavItem"><UserRound size={15}/><span>Account</span><ChevronDown className="workspaceChevron" size={13}/></div>
        <div className="workspaceLogout"><LogOut size={15}/>Logout</div>
      </aside>
      <div className="workspaceMain">
        <div className="workspaceHeader">
          <span/>
          <div className="workspaceUser"><Bell size={16}/><div className="userAvatar">PR</div><div><b>Priya Rao</b><small>priya@acme.example</small></div></div>
        </div>
        <div className="workspaceRoute">
          <div className="welcomePanel">
            <span className="miniKicker">WORKSPACE</span>
            <h3>Welcome, Priya</h3>
            <p>Your UnifyDesk workspace is ready. Use the navigation to work with contacts, conversations, broadcasts, templates and advertising.</p>
          </div>
          <div className="workspaceStats">
            <div><i/><small>Contacts</small><b>2,480</b><span>Customer profiles</span></div>
            <div><i/><small>Inbox</small><b>18</b><span>Open conversations</span></div>
            <div><i/><small>Broadcast</small><b>6</b><span>Recent campaigns</span></div>
          </div>
        </div>
      </div>
    </div>
  </div>
}

export default function Home(){
  return <><Header/><main className="marketingMain vibrantHome">
    <section className="marketingHero">
      <div className="heroGlow"/>
      <div className="statusBadge reveal"><span/>Customer communication, campaigns and growth — connected</div>
      <h1 className="reveal delay1">One workspace to run customer communication <em>without the tool sprawl.</em></h1>
      <p className="heroLead reveal delay1">UnifyDesk brings your inbox, customer context, broadcasts, email infrastructure and advertising workflows into one operating layer so teams can move faster without losing context.</p>
      <div className="heroActions reveal delay2">
        <Link className="button" href="/product">Explore the product <ArrowRight size={16}/></Link>
        <Link className="outlineButton compact" href="/integrations">View integrations</Link>
      </div>
      <WorkspacePreview/>
    </section>

    <section className="integrationStrip reveal">
      <p>Connect the providers you already use</p>
      <div>{integrations.map(name=><span key={name}><i/>{name}</span>)}</div>
    </section>

    <section className="aboutAttractiveSection">
      <div className="aboutCopy reveal">
        <p className="kicker">ABOUT UNIFYDESK</p>
        <h2>Built to make scattered customer work feel like one system.</h2>
        <p>Customer support, marketing, broadcasts and ads usually live in different tools. UnifyDesk is designed to give teams a cleaner operating rhythm with customer context at the center.</p>
        <div className="aboutChecklist">
          {[
            "Shared inbox for connected channels",
            "Customer profiles with consent and activity",
            "Broadcast and ad workflows in one workspace"
          ].map(item=><div key={item}><Check size={17}/><span>{item}</span></div>)}
        </div>
      </div>
      <div className="aboutVisual reveal delay1">
        <div className="aboutCircle"/>
        <div className="aboutCard bigCard"><b>One operating layer</b><p>Less switching between tools. More clarity for the team.</p></div>
        <div className="aboutCard statCard"><span>7+</span><small>integrations in progress</small></div>
        <div className="aboutCard smallCard"><MessageCircle size={18}/><b>Inbox + campaigns + ads</b></div>
      </div>
    </section>

    <section className="highlightBand">
      <div className="highlightBandInner">
        {[
          ["Shared customer context","Keep profiles, consent and recent activity connected across workflows."],
          ["Multi-channel operations","Manage conversations, broadcasts and ad workflows from one workspace."],
          ["Safer team controls","Company boundaries, roles and access control stay part of the foundation."]
        ].map(([title,body],i)=><article className={"reveal delay"+((i%3)+1)} key={title}><div className="featureIcon"><Layers3 size={20}/></div><h3>{title}</h3><p>{body}</p></article>)}
      </div>
    </section>

    <section className="connectedSection">
      <div className="connectedCopy reveal">
        <p className="darkKicker">ONE CONNECTED OPERATING LAYER</p>
        <h2>Your customers are everywhere. Your work doesn&apos;t have to be.</h2>
        <p>Keep conversations, audiences, broadcasts and advertising workflows connected so every team works with more context and fewer handoffs.</p>
      </div>
      <div className="flowVisual reveal delay1">
        <div className="flowSources"><span>WhatsApp</span><span>Email</span><span>SMS</span></div>
        <div className="flowHub"><b>UnifyDesk</b><small>Shared customer context · consent · audiences · activity</small></div>
        <div className="flowOutputs"><span>Broadcast</span><span>Google Ads</span><span>Meta Ads</span><span>Analytics</span></div>
      </div>
    </section>

    <section className="capabilitySection">
      <p className="kicker reveal">WHAT UNIFYDESK BRINGS TOGETHER</p>
      <h2 className="sectionTitle reveal">From first conversation to the next campaign.</h2>
      <p className="sectionIntro reveal">A connected set of workflows for support, marketing and business teams.</p>
      <div className="capabilityGrid">
        {features.map(([title,description,Icon],index)=>
          <article className={"capabilityCard reveal delay"+((index%3)+1)} key={title}>
            <div className="featureIcon"><Icon size={22}/></div>
            <h3>{title}</h3><p>{description}</p>
            <Link href="/product">Learn more <ArrowRight size={14}/></Link>
          </article>
        )}
      </div>
    </section>

    <section className="quoteSection reveal">
      <div className="quoteMark">“</div>
      <p>UnifyDesk is being shaped as a cleaner operating layer for customer-facing teams — not another disconnected dashboard, but a workspace where conversation, audience and campaign context stay connected.</p>
      <b>Customer communication · Broadcast · Ads</b>
    </section>

    <section className="featureShowcase">
      <div className="featureCopy reveal">
        <p className="kicker">UNIFIED INBOX</p>
        <h2>Every conversation, with the customer context beside it.</h2>
        <p>Give your team one place to work through connected customer conversations while keeping the profile, consent and recent activity close.</p>
        <ul>{["Shared team inbox","Customer context alongside messages","Workspace and role boundaries","Connected channel accounts"].map(x=><li key={x}><Check size={17}/>{x}</li>)}</ul>
      </div>
      <div className="inboxVisual reveal delay1">
        <div className="visualTop"><b>Inbox</b><span>12 open</span></div>
        <div className="inboxColumns">
          <div className="threadList">
            {["Priya Shah","Northstar Labs","Daniel Lee","Maya & Co."].map((name,i)=><div className={i===0?"selected":""} key={name}><b>{name}</b><small>{i===0?"WhatsApp · now":"Email · "+(i*8+5)+"m"}</small></div>)}
          </div>
          <div className="conversationCard">
            <b>Priya Shah</b><small>WhatsApp · Customer since 2025</small>
            <div className="activityCard"><span>RECENT ACTIVITY</span><b>Opened Autumn acquisition campaign</b></div>
            <div className="messageBubble">I&apos;d like to know more about the campaign options.</div>
            <div className="messageComposer"><span>Reply to Priya…</span><Send size={15}/></div>
          </div>
        </div>
      </div>
    </section>

    <section className="growthSection">
      <div className="growthHeading reveal">
        <div><p className="kicker">BROADCAST + AD CENTER</p><h2>Plan outreach and paid campaigns from the same operating layer.</h2></div>
        <p>Build audiences, reuse customer context and move between communication and advertising workflows without recreating setup in every provider dashboard.</p>
      </div>
      <div className="growthCards">
        <article className="broadcastCard reveal">
          <span className="softBadge">BROADCAST</span>
          <h3>One audience. Multiple connected channels.</h3>
          <p>Validate recipients, freeze the final audience and send with delivery visibility.</p>
          <div className="stepRow"><span>01 <b>Audience</b></span><span>02 <b>Preview</b></span><span>03 <b>Send</b></span></div>
          <div className="deliveryCard"><b>Delivery preview</b><span>872 recipients</span><i/><i/><i/></div>
        </article>
        <article className="adsCard reveal delay1">
          <span className="darkBadge">AD CENTER</span>
          <h3>Google + Meta campaign workflows, together.</h3>
          <p>Prepare objectives, budgets and creatives in one shared workspace while provider accounts stay underneath.</p>
          <div className="adStats"><span><small>Objective</small><b>Leads</b></span><span><small>Providers</small><b>2 connected</b></span><span><small>Creative</small><b>3 assets</b></span></div>
          <div className="setupCard"><small>AUTUMN ACQUISITION</small><b>Campaign setup</b><i/></div>
        </article>
      </div>
    </section>

    <section className="howSection">
      <p className="kicker reveal">HOW IT FITS TOGETHER</p>
      <h2 className="sectionTitle reveal">A simpler operating rhythm for customer work.</h2>
      <div className="howGrid">
        {[
          ["01","Connect","Bring supported inboxes, sending providers and ad accounts into the company workspace."],
          ["02","Centralize","Keep customers, consent, conversations and campaign context connected."],
          ["03","Activate","Run broadcasts and advertising workflows using shared audiences and business context."],
          ["04","Learn","Review delivery, campaign and operational activity without jumping between tools."]
        ].map(([n,t,b],i)=><article className={"reveal delay"+((i%3)+1)} key={n}><span>{n}</span><h3>{t}</h3><p>{b}</p></article>)}
      </div>
    </section>

    <section className="teamSection">
      <h2 className="sectionTitle reveal">One platform. Different teams. Shared customer context.</h2>
      <p className="sectionIntro reveal">Give each team the workflow it needs while keeping customer work connected across the business.</p>
      <div className="teamGrid">
        {[
          ["Business owners","Visibility","See communication and growth workflows in one workspace."],
          ["Customer support","Conversations","Work from a shared inbox with customer context close to every thread."],
          ["Marketing teams","Campaigns","Move between audiences, broadcasts and advertising without losing context."],
          ["Growing teams & agencies","Controls","Organize company access, roles and provider connections as workflows expand."]
        ].map(([t,tag,b],i)=><article className={"reveal delay"+((i%3)+1)} key={t}><span>{tag}</span><h3>{t}</h3><p>{b}</p></article>)}
      </div>
    </section>

    <section className="trustSection">
      <div className="trustCopy reveal"><p className="darkKicker">BUILT FOR TEAM WORKSPACES</p><h2>Control access without slowing the team down.</h2><p>Company isolation, role-based access, 2FA and session controls provide the foundations for safer customer work.</p></div>
      <div className="trustGrid">
        {[
          ["Company isolation","Keep workspace data separated by company.",Briefcase],
          ["Role-based access","Give people the access their work actually needs.",ShieldCheck],
          ["Two-factor authentication","Add stronger account protection for team members.",Zap],
          ["Session controls","Support safer sign-in and workspace access.",Layers3]
        ].map(([t,b,Icon]:any)=><article className="reveal" key={t}><Icon size={19}/><h3>{t}</h3><p>{b}</p></article>)}
      </div>
    </section>

    <section className="pricingSpotlight">
      <div className="pricingDecor pricingDecorLeft"/>
      <div className="pricingDecor pricingDecorRight"/>
      <div className="pricingSpotlightIntro reveal">
        <p className="darkKicker">PRICING MODEL</p>
        <h2>Simple workspace plans. Provider usage stays separate.</h2>
        <p>UnifyDesk subscriptions cover platform access and entitlements. Managed email, SMS delivery and advertising spend stay visible as separate provider usage.</p>
      </div>
      <div className="pricingSpotlightCard reveal delay1">
        <small>For growing teams</small>
        <h3>Growth</h3>
        <div className="priceLabel">Pricing at launch</div>
        <ul>{["Broadcast workflows","Ad Center workflows","Expanded team controls","Workspace growth support"].map(item=><li key={item}><Check size={15}/>{item}</li>)}</ul>
        <Link className="button small" href="/pricing">Explore pricing</Link>
      </div>
    </section>

    <section className="contactStyleCta">
      <div className="contactProfile reveal">
        <div className="contactAvatar">U</div>
        <div><small>READY TO SEE MORE?</small><h3>Explore the full UnifyDesk product direction.</h3><p>Review the product, solutions, integrations, pricing model and security direction from the updated website.</p></div>
      </div>
      <div className="contactPanel reveal delay1">
        <div className="contactRow"><span>Product overview</span><b>Available now</b></div>
        <div className="contactRow"><span>Integrations roadmap</span><b>Available now</b></div>
        <div className="contactRow"><span>Early access status</span><b>Coming soon</b></div>
        <div className="contactActions"><Link className="button" href="/product">Explore product</Link><Link className="outlineButton compact" href="/security">View security</Link></div>
      </div>
    </section>
  </main><Footer/></>
}