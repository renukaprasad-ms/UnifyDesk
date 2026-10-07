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
            We're preparing for early access. Public contact channels will be
            published before launch. In the meantime, explore these pages for
            product, integration and company information.
          </p>
        </section>
        <section className="contactGrid">
          <article>
            <Mail />
            <h2>Product & early access</h2>
            <p>
              Questions about the workspace, product capabilities or getting
              started.
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
            <p>Learn why we are building UnifyDesk and who it is for.</p>
            <Link className="textLink" href="/about">
              Get to know UnifyDesk
            </Link>
          </article>
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
