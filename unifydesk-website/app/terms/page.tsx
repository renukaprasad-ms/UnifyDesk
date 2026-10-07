import type {Metadata} from "next";
import {Header,Footer} from "../components";

export const metadata:Metadata={
  title:"Terms",
  description:"Pre-launch information about the service terms being prepared for UnifyDesk.",
  alternates:{canonical:"/terms"}
};

export default function Terms(){
  return <><Header/><main id="main-content" className="legal">
    <p className="kicker">LEGAL · PRE-LAUNCH</p>
    <h1>Terms of service</h1>
    <p className="legalUpdated">Updated 29 September 2026</p>
    <p className="legalIntro">UnifyDesk's final terms of service are being prepared for public launch. The sections below explain the areas those terms will cover and are not the final legal agreement.</p>
    <section><h2>Accounts and workspaces</h2><p>Customers will be responsible for maintaining authorized access to their accounts and company workspaces and for activity performed through those accounts.</p></section>
    <section><h2>Connected providers</h2><p>Use of third-party communication, email and advertising providers remains subject to the applicable provider's own terms, policies, fees and platform requirements.</p></section>
    <section><h2>Acceptable use</h2><p>Customers will be expected to use communication and campaign features lawfully, respect recipient consent and applicable platform policies, and avoid abusive or unauthorized activity.</p></section>
    <section><h2>Plans and provider costs</h2><p>UnifyDesk subscription terms will be separate from third-party delivery charges, advertising spend and other provider fees unless explicitly stated otherwise.</p></section>
    <aside>The complete binding terms, billing conditions, service limitations and legal provisions will be published before public launch.</aside>
  </main><Footer/></>
}

