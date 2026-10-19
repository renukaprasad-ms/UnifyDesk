import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Providers & Subprocessors",
  description: "Draft India Early Access Providers & Subprocessors for UnifyDesk",
  alternates: { canonical: "/subprocessors" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Authorized connected providers",
    "text": [
      "Depending on the customer's choice, Google/Gmail, Microsoft, Zoho and Meta/WhatsApp or Ads APIs may receive or provide mailbox, message and advertising data."
    ]
  },
  {
    "title": "Managed delivery",
    "text": [
      "Backend components include AWS SES and other email-delivery integrations, MSG91 and MetaReach for SMS. Provider enablement, contractual roles and sending location must be checked in the active environment."
    ]
  },
  {
    "title": "Infrastructure and storage",
    "text": [
      "The backend uses PostgreSQL, Redis and Kafka and contains adapters for S3-compatible and Supabase object storage. These code references do not establish the actual hosting supplier or region."
    ]
  },
  {
    "title": "Payment and analytics",
    "text": [
      "Payment processing is transitioning to Razorpay. The marketing site now gates optional Google Analytics behind a visitor choice; verify live behavior, active vendors, their terms and processing purposes."
    ]
  },
  {
    "title": "Before publication",
    "text": [
      "For every active vendor, record actual legal entity, purpose, data types, geographic location, transfer, contract/DPA, disclosure obligations and deletion arrangements. Separate independent customer-connected providers from UnifyDesk-appointed subprocessors."
    ]
  }
];
const related = [{"label":"Privacy","href":"/privacy"}];
export default function Page(){
  return <LegalDraftPage title="Providers & Subprocessors" intro="Engineering-derived conditional vendor inventory; not a final verified list of vendor legal entities." sections={sections} related={related}/>;
}
