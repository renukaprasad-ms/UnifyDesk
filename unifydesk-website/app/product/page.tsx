import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowRight,
  Check,
  FileText,
  Inbox,
  Radio,
  Target,
  Users,
  Building2,
  Layers3,
} from "lucide-react";
import { Footer, Header } from "../components";
import { WorkspaceDemo } from "../workspace-demo";
import { ChannelLinks } from "../channel-sections";

export const metadata: Metadata = {
  title: "Customer Communication Platform",
  description:
    "Explore UnifyDesk for shared inboxes, contacts, consent, email, SMS and WhatsApp broadcasts, templates, Google Ads, Meta Ads and team controls.",
  alternates: { canonical: "/product" },
};

const modules = [
  [
    "Unified Inbox",
    "WhatsApp, SMS and email conversations with channel context.",
    Inbox,
    ["Shared conversations", "Customer context", "Channel workflows"],
  ],
  [
    "Broadcast",
    "Audience validation, preview, scheduling and delivery visibility.",
    Radio,
    ["Audience workflows", "Templates and scheduling", "Delivery visibility"],
  ],
  [
    "Contacts",
    "Profiles, lists, segments, imports and consent context.",
    Users,
    ["Customer profiles", "Lists and segments", "Consent context"],
  ],
  [
    "Ad Center",
    "Google and Meta campaign workflows in one operating layer.",
    Target,
    ["Google + Meta workflows", "Campaign setup", "Performance context"],
  ],
  [
    "Templates",
    "Reusable communication templates across supported channels.",
    FileText,
    ["Reusable content", "Channel templates", "Team consistency"],
  ],
  [
    "Company & IAM",
    "Connections, plan, users, roles and permissions.",
    Building2,
    ["Provider connections", "Roles and permissions", "Plan controls"],
  ],
] as const;

export default function Product() {
  return (
    <>
      <Header />
      <main id="main-content" className="routePage">
        <section className="routeHero">
          <div className="statusBadge">
            <span />
            PRODUCT
          </div>
          <h1>One operating layer across the customer journey.</h1>
          <p>
            Explore a connected customer communication platform for shared
            conversations, contact and consent management, multichannel
            broadcasts, advertising workflows, templates and company controls.
          </p>
        </section>
        <section className="productTour" id="product-tour">
          <div className="routeCopy">
            <p className="kicker">TAKE A LOOK AROUND</p>
            <h2>Your workspace, from a few different angles.</h2>
            <p>
              Switch between the views to explore how conversations, contacts,
              broadcasts and advertising come together. This interactive tour
              uses sample data.
            </p>
            <Link className="textLink" href="/integrations">
              Check provider development status <ArrowRight size={15} />
            </Link>
          </div>
          <div>
            <WorkspaceDemo />
            <p className="demoCaption">
              Illustrative preview · Sample data · No live messages or campaigns
            </p>
          </div>
        </section>
        <section className="routeSection">
          <p className="kicker">CORE MODULES</p>
          <h2 className="sectionTitle">
            Built as focused modules. Connected by the workspace.
          </h2>
          <div className="routeCardGrid three">
            {modules.map(([title, description, Icon, points], i) => (
              <article
                className={"routeCard reveal delay" + ((i % 3) + 1)}
                key={title}
              >
                <div className="featureIcon">
                  <Icon size={22} />
                </div>
                <h3>{title}</h3>
                <p>{description}</p>
                <div className="pointList">
                  {points.map((p) => (
                    <span key={p}>
                      <Check size={15} />
                      {p}
                    </span>
                  ))}
                </div>
              </article>
            ))}
          </div>
        </section>
        <section className="splitRouteSection" id="shared-context">
          <div className="routeCopy reveal">
            <p className="kicker">SHARED CONTEXT</p>
            <h2>The modules are separate. The customer context is not.</h2>
            <p>
              Contacts, consent, conversations and campaign activity stay
              connected so support and marketing teams do not rebuild customer
              context for every workflow. Use the same operating layer for
              shared inbox work, audience building, email, SMS and WhatsApp
              broadcasts, and paid campaign coordination.
            </p>
            <Link className="textLink" href="/solutions">
              See how teams use it <ArrowRight size={15} />
            </Link>
          </div>
          <div className="contextStack reveal delay1">
            {[
              "Customer identity + consent",
              "Inbox + conversation activity",
              "Broadcast audiences + delivery",
              "Ad campaign context",
            ].map((x) => (
              <div key={x}>
                <Layers3 size={17} />
                <span>{x}</span>
              </div>
            ))}
          </div>
        </section>
        <ChannelLinks paths={["/whatsapp-shared-inbox","/omnichannel-inbox","/whatsapp-broadcast","/sms-marketing","/email-campaigns"]} title="Explore one workflow in more detail."/>
        <section className="centerRouteCta reveal">
          <h2>See the provider layer behind the workflows.</h2>
          <p>
            UnifyDesk keeps providers visible while simplifying how your team
            works across them.
          </p>
          <Link className="button" href="/integrations">
            View integrations <ArrowRight size={16} />
          </Link>
        </section>
      </main>
      <Footer />
    </>
  );
}
