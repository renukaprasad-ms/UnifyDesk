import type { Metadata } from "next";
export const metadata: Metadata = {
  title: "Contact",
  description:
    "Contact UnifyDesk about customer communication software, product access, integrations, technology partnerships and business enquiries.",
  alternates: { canonical: "/contact" },
};
import Link from "next/link";
import { Mail, Handshake, Plug } from "lucide-react";
import { Header, Footer } from "../components";
export default function Contact() {
  return (
    <>
      <Header />
      <main id="main-content">
        <section className="pageHero">
          <p className="kicker">CONTACT</p>
          <h1>Let's talk about UnifyDesk.</h1>
          <p>
            We're preparing for early access. For product support, privacy,
            grievances and data-deletion requests, email{" "}
            <a href="mailto:support@unifydesk.in">support@unifydesk.in</a>.
            For billing or business replies, email{" "}
            <a href="mailto:reply@unifydesk.in">reply@unifydesk.in</a>.
          </p>
        </section>
        <section className="contactGrid">
          <article>
            <Mail />
            <h2>Product & early access</h2>
            <p>
              Questions about the workspace, product capabilities, privacy or getting
              started. Contact{" "}<a href="mailto:support@unifydesk.in">support@unifydesk.in</a>.
            </p>
            <Link className="textLink" href="/product#product-tour">
              Explore the product tour
            </Link>
          </article>
          <article>
            <Plug />
            <h2>Integrations</h2>
            <p>
              See which providers are in development, planned or being
              evaluated.
            </p>
            <Link className="textLink" href="/integrations">
              See integration status
            </Link>
          </article>
          <article>
            <Handshake />
            <h2>Business</h2>
            <p>Learn why we are building UnifyDesk and who it is for. For billing or business replies, contact{" "}<a href="mailto:reply@unifydesk.in">reply@unifydesk.in</a>.</p>
            <Link className="textLink" href="/about">
              Get to know UnifyDesk
            </Link>
          </article>
        </section>
        <section className="startupIdentity contactBusiness" aria-labelledby="business-details-heading">
          <div className="startupIdentityIntro">
            <p className="kicker">BUSINESS INFORMATION</p>
            <h2 id="business-details-heading">A product company in its early stage.</h2>
            <p>
              UnifyDesk is an India-based, independently developed B2B SaaS
              initiative operating as a sole proprietorship in Karnataka.
              We are preparing for early access; this website describes the
              product and development roadmap, not a generally available paid service.
            </p>
          </div>
          <dl className="startupFacts">
            <div><dt>Location</dt><dd>Karnataka, India</dd></div>
            <div><dt>Business enquiries</dt><dd><a href="mailto:reply@unifydesk.in">reply@unifydesk.in</a></dd></div>
            <div><dt>Support and privacy</dt><dd><a href="mailto:support@unifydesk.in">support@unifydesk.in</a></dd></div>
          </dl>
        </section>
        <section className="centerCta">
          <h2>Public access is coming soon.</h2>
          <p className="ctaSupport">
            Sign-up is not open yet while UnifyDesk is under active development.
          </p>
          <Link className="button" href="/product">
            Explore UnifyDesk
          </Link>
        </section>
      </main>
      <Footer />
    </>
  );
}
