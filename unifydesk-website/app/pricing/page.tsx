import type { Metadata } from "next";
import { Check } from "lucide-react";
import Link from "next/link";
import { Footer, Header } from "../components";
import { ChannelLinks, ContentFaq } from "../channel-sections";

export const metadata: Metadata = {
  title: "Pricing for Customer Communication Teams",
  description:
    "Explore UnifyDesk pricing for customer communication, shared inbox, broadcasts, advertising workflows and managed messaging services.",
  alternates: { canonical: "/pricing" },
};

const plans = [
  [
    "Free",
    "Explore the core workspace and validate your setup.",
    "₹0",
    ["Core workspace", "Starter user limits", "Basic connections"],
    false,
  ],
  [
    "Starter",
    "For small teams centralizing customer communication.",
    "Pricing coming soon",
    ["Everything in Free", "Higher workspace limits", "Company controls"],
    false,
  ],
  [
    "Growth",
    "For teams running communication and campaigns together.",
    "Pricing coming soon",
    ["Broadcast workflows", "Ad Center workflows", "Expanded team controls"],
    true,
  ],
] as const;

export default function Pricing() {
  return (
    <>
      <Header />
      <main id="main-content" className="routePage">
        <section className="routeHero">
          <div className="statusBadge">
            <span />
            PRICING
          </div>
          <h1>Plans for the workspace. Provider usage stays separate.</h1>
          <p>
            Public pricing is being finalized. UnifyDesk separates platform
            access for customer communication, shared inbox, contacts,
            broadcasts and advertising workflows from managed email, SMS and
            third-party advertising spend.
          </p>
        </section>
        <section className="routeSection">
          <div className="pricingRouteGrid">
            {plans.map(([name, description, price, items, featured]) => (
              <article
                className={
                  (featured ? "featured " : "") + "routePricingCard reveal"
                }
                key={name}
              >
                {featured && <span className="popular">For growing teams</span>}
                <h2>{name}</h2>
                <p>{description}</p>
                <strong>{price}</strong>
                <div className="pointList">
                  {items.map((x) => (
                    <span key={x}>
                      <Check size={15} />
                      {x}
                    </span>
                  ))}
                </div>
                <Link
                  className={featured ? "button" : "outlineButton"}
                  href="/product#product-tour"
                >
                  Explore included workflows
                </Link>
              </article>
            ))}
          </div>
          <p className="pricingAvailability">
            Public sign-up is coming soon. Paid plan prices and limits will be
            published before launch.
          </p>
        </section>
        <section className="routeSection soft">
          <p className="kicker">BILLING MODEL</p>
          <h2 className="sectionTitle">
            Two layers keep billing understandable.
          </h2>
          <div className="routeCardGrid three">
            {[
              [
                "Platform subscription",
                "The platform plan controls users, workspace limits, feature access and company-level entitlements. Choose the access the team needs for conversations, contacts and campaign preparation. Final paid prices and exact limits will be published before launch.",
              ],
              [
                "Managed services",
                "Managed email and SMS have a separate usage layer based on sending and applicable rates. A workspace plan should not be interpreted as unlimited or free delivery. Confirm sender readiness, supported routes and final usage terms before budgeting a campaign.",
              ],
              [
                "Advertising spend",
                "Google and Meta media spend stays with the connected advertising providers. Platform access organizes the campaign workflow; it does not pay for the ads. Review account permissions and provider budgets alongside the workspace plan.",
              ],
            ].map(([t, b]) => (
              <article className="routeCard reveal" key={t}>
                <h3>{t}</h3>
                <p>{b}</p>
              </article>
            ))}
          </div>
        </section>
        <section className="splitRouteSection"><div className="routeCopy"><p className="kicker">PLAN THE WORK FIRST</p><h2>Evaluate access and delivery separately.</h2><p>Start with the team size, company controls and workflows you need. Then estimate the volume and channels of customer outreach. Managed messaging costs depend on the provider and sending setup; external advertising budgets remain a separate decision.</p><p>The Free, Starter and Growth cards show the current plan direction. Public sign-up is coming soon. Paid prices, final entitlements, usage rates and billing terms must be confirmed before purchase.</p><Link className="textLink" href="/integrations">Check provider availability</Link></div><div className="seoPreparation"><h3>Questions to bring to your evaluation</h3><ul><li><Check size={18}/><span>How many members need access, and which roles should manage providers?</span></li><li><Check size={18}/><span>Which channels and sender accounts are ready for customer work?</span></li><li><Check size={18}/><span>What sending volume and external advertising budget do you expect?</span></li></ul><Link className="textLink" href="/security">Review company and account controls</Link></div></section>
        <ContentFaq title="Know what each cost covers." items={[
          {title:"Does the Free plan include free email or SMS delivery?",body:"The ₹0 card describes platform access. Managed email and SMS usage are separate, and no free sending allowance is promised here. Final rates and entitlements will be published before public launch."},
          {title:"Are Google Ads and Meta Ads budgets part of the subscription?",body:"No. Media spend stays with Google and Meta. The UnifyDesk plan concerns workspace access and supported workflows, while the connected provider account carries its advertising budget."},
          {title:"Can I buy Starter or Growth today?",body:"Public sign-up and paid pricing are coming soon. Use the product tour to evaluate the developing workspace. Paid prices, exact limits and commercial terms will be available before launch."}
        ]}/>
        <ChannelLinks paths={["/email-campaigns","/sms-marketing","/whatsapp-broadcast"]} title="Match your sending plan to the channel."/>
      </main>
      <Footer />
    </>
  );
}
