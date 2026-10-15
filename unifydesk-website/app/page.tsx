import type { Metadata } from "next";
import Link from "next/link";
import {
  ArrowDown,
  ArrowRight,
  Check,
  ChevronRight,
  Inbox,
  Mail,
  MessageCircle,
  Radio,
  ShieldCheck,
  Target,
  TrendingUp,
  Users,
  Workflow,
} from "lucide-react";
import { Footer, Header } from "./components";
import { WorkspaceDemo } from "./workspace-demo";
import { ChannelLinks } from "./channel-sections";

export const metadata: Metadata = {
  title: {
    absolute: "UnifyDesk | Customer Communication & Marketing Platform",
  },
  description:
    "Bring customer conversations, contacts, broadcasts, CRM pipelines and advertising into one connected UnifyDesk workspace.",
  alternates: { canonical: "/" },
};

const capabilities = [
  {
    title: "A little more context. A much better conversation.",
    label: "UNIFIED INBOX",
    body: "Give your team a shared place for connected conversations, with the customer story close at hand.",
    icon: Inbox,
    className: "inboxFeature",
    points: [
      "Connected channel accounts",
      "Customer context beside every thread",
    ],
  },
  {
    title: "Close deals faster with full context.",
    label: "SALES CRM & PIPELINES",
    body: "Track deals across visual pipeline stages, score high-intent leads, share public booking links and capture leads with embeddable web forms.",
    icon: TrendingUp,
    className: "crmFeature",
    points: [
      "Visual deal pipelines & forecasting",
      "Public booking pages & lead capture forms",
    ],
  },
  {
    title: "Know the person behind the message.",
    label: "CONTACTS",
    body: "Bring profiles, lists, segments and consent together. Build a useful customer record your whole team can work from.",
    icon: Users,
    className: "contactsFeature",
    points: ["Lists & segments", "Channel-level consent"],
  },
  {
    title: "The right audience. The next conversation.",
    label: "BROADCAST",
    body: "Prepare your message, preview your audience and follow delivery across supported email, SMS and WhatsApp workflows.",
    icon: Radio,
    className: "broadcastFeature",
    points: ["Audience preview", "Scheduling & delivery visibility"],
  },
  {
    title: "Give your growth workflows a home.",
    label: "AD CENTER",
    body: "Coordinate Google and Meta campaign workflows from the same workspace your team already uses.",
    icon: Target,
    className: "adsFeature",
    points: ["Connected ad accounts", "Campaign setup & performance context"],
  },
];

const faqs = [
  [
    "What is UnifyDesk?",
    "UnifyDesk is a customer communication and marketing workspace. It brings together connected inboxes, contacts and consent, broadcasts, templates, and Google and Meta advertising workflows.",
  ],
  [
    "Can I start using UnifyDesk today?",
    "UnifyDesk is under active development. Public sign-up is not open yet. You can explore the product tour and integration roadmap here while we prepare for early access.",
  ],
  [
    "Which channels and providers are being supported?",
    "The current development scope includes WhatsApp, email and SMS workflows, Gmail and Microsoft inbox connections, managed email, MSG91 SMS, Google Ads and Meta Ads. The integrations page shows development, planned and evaluation status for each provider.",
  ],
  [
    "Are messaging costs included in a plan?",
    "Platform subscriptions cover workspace access and plan entitlements. Managed email and SMS usage are charged separately. Google and Meta advertising spend stays with the connected provider. Public plan pricing is being finalized.",
  ],
];

export default function Home() {
  return (
    <>
      <Header />
      <main id="main-content" className="homePage">
        <section className="homeHero">
          <div className="heroGrid">
            <div className="heroCopy reveal">
              <span className="heroEyebrow">
                <span className="statusDot" />A better home for customer work
              </span>
              <h1>
                Every conversation.
                <br />
                One connected
                <br />
                <em>workspace.</em>
              </h1>
              <p>
                Bring your inbox, contacts, broadcasts and ads together. Give
                your team the context to make every interaction count.
              </p>
              <div className="heroActions">
                <Link className="button" href="/product">
                  Explore UnifyDesk <ArrowRight size={17} />
                </Link>
                <a className="heroSecondary" href="#platform">
                  See how it connects <ArrowDown size={16} />
                </a>
              </div>
              <div className="heroChannelNote">
                <Mail size={14} />
                <span>Email</span>
                <MessageCircle size={14} />
                <span>WhatsApp</span>
                <Radio size={14} />
                <span>SMS</span>
              </div>
            </div>
            <div className="heroProduct reveal delay1">
              <WorkspaceDemo />
              <p className="demoCaption">
                Explore the workspace tabs · Illustrative preview with sample
                data
              </p>
            </div>
          </div>
          <div className="heroBottom">
            <span>Customer conversations. Campaigns. Growth.</span>
            <span>
              Connected by context <Workflow size={16} />
            </span>
          </div>
        </section>

        <section
          className="providerStrip"
          aria-label="Provider development scope"
        >
          <p>Designed around the providers you use</p>
          <div>
            {[
              "WhatsApp",
              "Gmail",
              "Microsoft",
              "Amazon SES",
              "MSG91",
              "Google Ads",
              "Meta Ads",
            ].map((name) => (
              <span key={name}>{name}</span>
            ))}
          </div>
          <Link className="textLink" href="/integrations">
            See integration status <ArrowRight size={14} />
          </Link>
        </section>

        <section id="platform" className="platformSection sectionSpace">
          <div className="sectionHeading">
            <div>
              <p className="kicker">LESS SWITCHING. MORE CONNECTION.</p>
              <h2>
                Your customer work,
                <br />
                finally on the same page.
              </h2>
            </div>
            <p>
              Conversations in one tool. Audiences in another. Campaigns
              somewhere else. UnifyDesk brings the work together around a shared
              customer record.
            </p>
          </div>
          <div className="featureBento">
            {capabilities.map(
              ({ title, label, body, icon: Icon, className, points }) => (
                <article className={`featureTile ${className}`} key={label}>
                  <div className="tileHeading">
                    <div className="featureIcon">
                      <Icon size={23} />
                    </div>
                    <span className="tileLabel">{label}</span>
                  </div>
                  <h3>{title}</h3>
                  <p>{body}</p>
                  <ul>
                    {points.map((point) => (
                      <li key={point}>
                        <Check size={15} />
                        {point}
                      </li>
                    ))}
                  </ul>
                  <Link className="tileLink" href="/product">
                    Explore {label.toLowerCase()} <ArrowRight size={16} />
                  </Link>
                </article>
              ),
            )}
          </div>
        </section>

        <section className="contextSection sectionSpace">
          <div className="contextCopy">
            <p className="kicker">A CUSTOMER, NOT JUST A CONTACT.</p>
            <h2>
              The story stays
              <br />
              with the customer.
            </h2>
            <p>
              A conversation is more useful when you know what came before it.
              Keep profiles, consent and activity connected as your team moves
              from support to outreach.
            </p>
            <Link className="textLink" href="/product#shared-context">
              Discover shared context <ArrowRight size={16} />
            </Link>
          </div>
          <article
            className="customerExample"
            aria-label="Illustrative customer record"
          >
            <div className="customerExampleHeader">
              <div className="customerAvatar">PS</div>
              <div>
                <h3>Priya Shah</h3>
                <p>priya@example.com</p>
              </div>
              <span className="sampleBadge">Sample contact</span>
            </div>
            <div className="customerFacts">
              <div>
                <span>List</span>
                <b>Product updates</b>
              </div>
              <div>
                <span>Email consent</span>
                <b className="consentValue">
                  <Check size={13} />
                  Opted in
                </b>
              </div>
            </div>
            <div className="customerTimeline">
              <p className="miniKicker">CUSTOMER ACTIVITY</p>
              {[
                [
                  MessageCircle,
                  "A conversation, with context",
                  "Asked about campaign options in the shared inbox.",
                ],
                [
                  Users,
                  "A profile your team can work from",
                  "Contact details, list membership and consent together.",
                ],
                [
                  Radio,
                  "A more informed next step",
                  "Use consent and audience rules to prepare outreach.",
                ],
              ].map(([Icon, title, body]) => {
                const ActivityIcon = Icon as typeof Users;
                return (
                  <div key={String(title)}>
                    <span className="timelineIcon">
                      <ActivityIcon size={16} />
                    </span>
                    <div>
                      <b>{String(title)}</b>
                      <p>{String(body)}</p>
                    </div>
                  </div>
                );
              })}
            </div>
            <div className="customerExampleFooter">
              <ShieldCheck size={15} />
              Customer context travels across workflows.
            </div>
          </article>
        </section>

        <section className="workflowSection sectionSpace">
          <p className="kicker">FROM SETUP TO THE NEXT SEND</p>
          <div className="sectionHeading">
            <h2>
              A clearer way
              <br />
              to get work moving.
            </h2>
            <Link className="outlineButton" href="/solutions">
              Find your team&apos;s workflow <ArrowRight size={16} />
            </Link>
          </div>
          <div className="workflowSteps">
            {[
              [
                "01",
                "Connect your channels",
                "Bring supported inboxes, sending providers and ad accounts into your company workspace.",
              ],
              [
                "02",
                "Bring context together",
                "Organize customer profiles, consent, lists and segments for the work ahead.",
              ],
              [
                "03",
                "Start the next conversation",
                "Work through your inbox or prepare a broadcast with audience and delivery visibility.",
              ],
            ].map(([n, title, body]) => (
              <article key={n}>
                <span>{n}</span>
                <h3>{title}</h3>
                <p>{body}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="workspacePromise sectionSpace">
          <div>
            <span className="securityEyebrow">
              <ShieldCheck size={18} />
              BUILT FOR TEAM WORKSPACES
            </span>
            <h2>
              Room to collaborate.
              <br />
              Controls to keep it clear.
            </h2>
            <p>
              Keep company boundaries, roles, account protection and provider
              access part of how your team works.
            </p>
            <Link className="whiteButton" href="/security">
              Explore workspace controls <ArrowRight size={16} />
            </Link>
          </div>
          <div className="promiseList">
            {[
              [
                "Company workspaces",
                "Keep customer work organized by company.",
              ],
              [
                "Roles & permissions",
                "Give team members access for the work they do.",
              ],
              [
                "Account protection",
                "Two-factor authentication and session controls.",
              ],
              [
                "Transparent usage",
                "Platform plans and provider usage stay separate.",
              ],
            ].map(([title, body]) => (
              <article key={title}>
                <Check size={19} />
                <div>
                  <h3>{title}</h3>
                  <p>{body}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section className="faqSection sectionSpace">
          <div>
            <p className="kicker">A FEW GOOD QUESTIONS</p>
            <h2>
              Let&apos;s make
              <br />
              things clear.
            </h2>
            <Link className="textLink" href="/pricing">
              Explore the pricing model <ArrowRight size={16} />
            </Link>
          </div>
          <div className="faqList">
            {faqs.map(([question, answer]) => (
              <details key={question}>
                <summary>
                  {question}
                  <ChevronRight size={18} />
                </summary>
                <p>{answer}</p>
              </details>
            ))}
          </div>
        </section>

        <ChannelLinks paths={["/best-omnichannel-broadcast-india","/whatsapp-shared-inbox","/whatsapp-broadcast","/sms-marketing","/email-campaigns","/omnichannel-inbox"]} title="Find the customer workflow your team needs." intro="Explore conversations and campaign preparation by channel, including provider readiness, consent and the developing product experience."/>
        <section className="closingCta">
          <div>
            <p className="kicker">MEET YOUR NEXT WORKSPACE</p>
            <h2>
              Better connected.
              <br />
              Better together.
            </h2>
            <p>
              Take a closer look at what we&apos;re building for customer-facing
              teams.
            </p>
          </div>
          <div>
            <Link className="button" href="/product">
              Take the product tour <ArrowRight size={18} />
            </Link>
            <span>
              <span className="statusDot" />
              In development · Public access coming soon
            </span>
          </div>
        </section>
      </main>
      <Footer />
    </>
  );
}
