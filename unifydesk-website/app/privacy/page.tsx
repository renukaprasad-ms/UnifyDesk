import type {Metadata} from "next";
import {Header,Footer} from "../components";

export const metadata:Metadata={
  title:"Privacy",
  description:"Pre-launch privacy information for UnifyDesk account, workspace and connected-provider data.",
  alternates:{canonical:"/privacy"}
};

export default function Privacy(){
  return <><Header/><main id="main-content" className="legal">
    <p className="kicker">LEGAL · PRE-LAUNCH</p>
    <h1>Privacy</h1>
    <p className="legalUpdated">Updated 29 September 2026</p>
    <p className="legalIntro">This page describes the privacy areas UnifyDesk is preparing to document for public launch. It is not a substitute for the final legal privacy notice.</p>
    <section><h2>Information handled by the service</h2><p>UnifyDesk may process account and company information, workspace configuration, customer communication data, connected-provider identifiers and operational records needed to provide the product.</p></section>
    <section><h2>Connected services</h2><p>When a company connects services such as communication, email or advertising providers, UnifyDesk processes the information required to operate that connection and the workflows the company chooses to use.</p></section>
    <section><h2>Security and access</h2><p>Access is organized around authenticated accounts, company boundaries and role-based permissions. Additional controls include supported two-factor authentication and session-management flows.</p></section>
    <section><h2>Retention and deletion</h2><p>Product flows are being designed to make data lifecycle choices visible, including retention behavior around disconnected channels where applicable.</p></section>
    <aside>The complete privacy notice, including legal bases, retention periods, subprocessors, rights and contact information, will be published before public launch.</aside>
  </main><Footer/></>
}

