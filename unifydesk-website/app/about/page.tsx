import type { Metadata } from "next";
export const metadata: Metadata = {
  title: "About",
  description:
    "Learn why UnifyDesk is building a connected customer communication and marketing platform for shared inboxes, broadcasts, advertising and business teams.",
  alternates: { canonical: "/about" },
};
import Link from "next/link";
import { Layers3, Users, Workflow } from "lucide-react";
import { Header, Footer } from "../components";
export default function About() {
  return (
    <>
      <Header />
      <main id="main-content">
        <section className="pageHero">
          <p className="kicker">ABOUT UNIFYDESK</p>
          <h1>Customer work should feel like one system.</h1>
          <p>
            UnifyDesk is being built around a simple idea: communication,
            outreach and advertising should not force a growing business to
            operate from a collection of disconnected dashboards.
          </p>
        </section>
        <section className="storyGrid">
          <article>
            <Layers3 />
            <h2>Why we're building it</h2>
            <p>
              Businesses add inboxes, delivery providers, campaign tools and
              advertising platforms as they grow. Each solves a problem, but the
              customer context gets fragmented between them.
            </p>
          </article>
          <article>
            <Workflow />
            <h2>What we're changing</h2>
            <p>
              UnifyDesk creates a common operating layer across those workflows
              while allowing the underlying providers to keep doing the jobs
              they are good at.
            </p>
          </article>
          <article>
            <Users />
            <h2>Who it's for</h2>
            <p>
              Professionals, business owners, support teams, marketers and
              agencies that want clearer workflows without constantly switching
              tools.
            </p>
          </article>
        </section>
        <section className="startupIdentity" aria-labelledby="startup-identity-heading">
          <div className="startupIdentityIntro">
            <p className="kicker">AN INDEPENDENT STARTUP IN INDIA</p>
            <h2 id="startup-identity-heading">Built for growing teams. Starting with the essentials.</h2>
            <p>
              UnifyDesk is an independently developed, self-funded B2B SaaS initiative
              based in Karnataka, India, and operating as a sole proprietorship.
              We are building and testing the platform ahead of early access;
              public sign-up and paid plans are not yet available.
            </p>
          </div>
          <dl className="startupFacts">
            <div><dt>Based in</dt><dd>Karnataka, India</dd></div>
            <div><dt>Business structure</dt><dd>Sole proprietorship</dd></div>
            <div><dt>Current stage</dt><dd>Bootstrapped · Preparing for early access</dd></div>
          </dl>
          <p className="startupIdentityContact">
            Want to discuss UnifyDesk or its early-access roadmap?
            {" "}<Link href="/contact">Get in touch</Link>.
          </p>
        </section>
        <section className="centerCta">
          <h2>Build customer workflows around one workspace.</h2>
          <p className="ctaSupport">UnifyDesk is under active development.</p>
          <Link className="button" href="/product#product-tour">
            Take the product tour
          </Link>
        </section>
      </main>
      <Footer />
    </>
  );
}
