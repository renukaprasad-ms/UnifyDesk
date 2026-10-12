import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Acceptable Use Policy",
  description: "Draft India Early Access Acceptable Use Policy for UnifyDesk",
  alternates: { canonical: "/acceptable-use" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Permission-based marketing",
    "text": [
      "Customers must have a valid legal basis and necessary provider-specific permissions before sending broadcasts. A contact import or possession of an email/phone number is not marketing consent. Honor unsubscribes, complaints, suppression, WhatsApp policies and Indian SMS/DLT obligations."
    ]
  },
  {
    "title": "Prohibited actions",
    "text": [
      "No spam, malware, deceptive offers, unlawful promotions, harassment, identity impersonation, phishing, account takeover, scraping or unauthorized third-party data uploads."
    ]
  },
  {
    "title": "Platform safety",
    "text": [
      "Do not circumvent tenant boundaries, limits, throttles, channel restrictions, abuse detection, subscription entitlements or security systems."
    ]
  },
  {
    "title": "Enforcement and complaints",
    "text": [
      "The final contract must explain investigation, sending restriction, suspension and appeals. Proposed abuse reporting contact: support@unifydesk.in. Confirm inbox delivery, triage and escalation before publication."
    ]
  }
];
const related = [{"label":"Terms","href":"/terms"}];
export default function Page(){
  return <LegalDraftPage title="Acceptable Use Policy" intro="Draft conduct and recipient-protection rules for business customers." sections={sections} related={related}/>;
}
