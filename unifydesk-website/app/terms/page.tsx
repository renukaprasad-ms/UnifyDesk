import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Terms of Service",
  description: "Draft India Early Access Terms of Service for UnifyDesk",
  alternates: { canonical: "/terms" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Business identity and acceptance",
    "text": [
      "The service is currently operated as an Indian sole proprietorship. Confirm the legal proprietor, enterprise address and effective date before publishing. Proposed service and grievance contact: support@unifydesk.in. Billing and payment disputes: reply@unifydesk.in. Both email routes require end-to-end verification before publication.",
      "Customers must have authority to open and manage business workspaces and are responsible for their authorized users."
    ]
  },
  {
    "title": "Service description",
    "text": [
      "UnifyDesk may provide CRM and contact tools, team inboxes, communication broadcasts, templates, managed email and SMS, WhatsApp connectivity and advertising integrations. Early Access features may be restricted, unstable or unavailable."
    ]
  },
  {
    "title": "Connected providers",
    "text": [
      "Connecting Google, Microsoft, Zoho, Meta and other external services requires permission from the account owner and compliance with each provider's own terms, scope policies and fees.",
      "Removing provider authorization does not necessarily delete messages or metadata already synced to UnifyDesk."
    ]
  },
  {
    "title": "Acceptable use and marketing consent",
    "text": [
      "Customers must not use UnifyDesk for spam, impersonation, phishing, unlawful advertising or messages without required recipient permissions. India-specific SMS DLT, unsubscribe and channel-specific platform policies remain applicable."
    ]
  },
  {
    "title": "Plans and payments",
    "text": [
      "Platform subscriptions and separately priced pay-as-you-go managed communications may apply. Actual prices, tax, plan limits, renewal consent, invoicing, downgrade timing and billable events need an approved order and matching backend verification.",
      "Payment processing is being migrated to Razorpay. One-time checkout, renewal authorization, wallet auto-top-up, cancellations and refunds must be verified against the deployed system before public billing terms are approved."
    ]
  },
  {
    "title": "Customer content and rights",
    "text": [
      "Customers remain responsible for the content and contacts they submit and grant UnifyDesk the permissions necessary to process that content to deliver their selected services.",
      "The Privacy Policy and any duly executed Data Processing Addendum govern the relevant data-processing arrangements."
    ]
  },
  {
    "title": "Suspension, termination and deletion",
    "text": [
      "Abuse or security issues may require restriction subject to the final enforcement agreement. Account cancellation, data export, disconnection, background deletion and billing record preservation require consistent published procedures."
    ]
  },
  {
    "title": "Clauses awaiting counsel",
    "text": [
      "Governing law, disputes, liability, warranties, indemnities, notice procedure, refund/cancellation rules and legally required consumer disclosures must be approved. No unsupported limitation of liability, jurisdiction, support SLA or refund period is asserted here."
    ]
  }
];
const related = [{"label":"Acceptable Use","href":"/acceptable-use"},{"label":"Cancellation & Refunds","href":"/refund-policy"},{"label":"Privacy","href":"/privacy"}];
export default function Page(){
  return <LegalDraftPage title="Terms of Service" intro="Draft business SaaS terms for UnifyDesk's India Early Access; no binding contract exists through this draft." sections={sections} related={related}/>;
}
