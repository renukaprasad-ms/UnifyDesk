import type {Metadata} from "next";
import Link from "next/link";
import {Inbox,Radio,Target,Users,BarChart3,Mail,ArrowRight,Check} from "lucide-react";
import {Header,Footer} from "../components";

export const metadata:Metadata={
  title:"Customer Communication Platform",
  description:"Explore what UnifyDesk is building: unified inbox, broadcasts, customer context, analytics, email infrastructure and Google and Meta advertising workflows. Public access is coming soon.",
  alternates:{canonical:"/product"}
};

const items=[
  {
    title:"Unified Inbox",
    description:"A shared workspace for customer conversations with the customer context beside every thread.",
    icon:Inbox,
    points:["Shared conversations","Customer context","Team collaboration"]
  },
  {
    title:"Broadcast",
    description:"Plan targeted outreach across connected communication providers without rebuilding the workflow for every channel.",
    icon:Radio,
    points:["Audience workflows","Templates and scheduling","Delivery visibility"]
  },
  {
    title:"Ad Center",
    description:"Prepare Google and Meta advertising workflows from one operating layer while keeping the providers underneath.",
    icon:Target,
    points:["Google + Meta workflows","Shared campaign setup","Performance context"]
  },
  {
    title:"Customers",
    description:"Keep customer identity, conversation history and activity connected across communication and campaign workflows.",
    icon:Users,
    points:["Unified profiles","Conversation history","Activity context"]
  },
  {
    title:"Analytics",
    description:"Bring communication, broadcast and campaign activity into a clearer business view as the platform matures.",
    icon:BarChart3,
    points:["Workspace reporting","Campaign context","Operational visibility"]
  },
  {
    title:"Email Infrastructure",
    description:"Connect supported email providers and managed sending options while keeping delivery architecture visible.",
    icon:Mail,
    points:["Provider connections","Managed sending options","Delivery controls"]
  }
];

export default function Product(){
  return <><Header/><main>
    <section className="pageHero productHero">
      <p className="kicker">PRODUCT · IN DEVELOPMENT</p>
      <h1>One workspace across the customer journey.</h1>
      <p>UnifyDesk is under active development. The capabilities below describe what we are building, not a promise that every feature is publicly available today.</p>
      <div className="productHeroStatus"><span className="statusDot"/>Public access coming soon</div>
    </section>

    <section className="productRoadmapIntro">
      <div>
        <p className="kicker">WHAT WE'RE BUILDING</p>
        <h2>A connected operating layer, built in focused modules.</h2>
      </div>
      <p>Each module is being implemented and tested before broader access. Availability can change while development continues.</p>
    </section>

    <section className="productFeatures">
      {items.map(({title,description,icon:Icon,points},i)=>
        <article className="productFeatureCard" key={title}>
          <div className="productFeatureHead">
            <span className="number">0{i+1}</span>
            <span className="featureStatus"><span/>In development</span>
          </div>
          <div className="icon productFeatureIcon"><Icon/></div>
          <h2>{title}</h2>
          <p>{description}</p>
          <div className="productFeaturePoints">
            {points.map(point=><span key={point}><Check/>{point}</span>)}
          </div>
        </article>
      )}
    </section>

    <section className="centerCta productBottomCta">
      <p className="kicker">PRE-LAUNCH</p>
      <h2>Provider connections and availability will expand as development progresses.</h2>
      <p className="ctaSupport">See the integrations we are planning and building for UnifyDesk.</p>
      <Link className="button" href="/integrations">View integrations <ArrowRight size={17}/></Link>
    </section>
  </main><Footer/></>
}
