import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Cancellation & Refund Policy",
  description: "Draft India Early Access Cancellation & Refund Policy for UnifyDesk",
  alternates: { canonical: "/refund-policy" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Charges",
    "text": [
      "Platform plans may be priced separately from metered managed email/SMS and external advertising/provider costs. Disclose billable units, taxes, invoice timing and recipient eligibility clearly before charging."
    ]
  },
  {
    "title": "Cancellation and renewal",
    "text": [
      "Confirm the actual cancellation UI, recurring mandates, downgrade effective dates, renewal schedules and whether partial-period credits exist. Do not advertise a refund window or instant cancellation without product and legal approval."
    ]
  },
  {
    "title": "Disputes and refunds",
    "text": [
      "Confirm handling of duplicate collections, gateway errors, delivery unknown states, refunds on unused periods, tax reversals, and customer support escalation.",
      "Verified billing contact: [CONFIRM BILLING EMAIL]. The reviewed backend integrates Cashfree but defaults to disabled; actual activation requires verification."
    ]
  }
];
const related = [{"label":"Terms","href":"/terms"}];
export default function Page(){
  return <LegalDraftPage title="Cancellation & Refund Policy" intro="Draft billing policy for plan subscriptions and managed-service usage; commercial decisions remain pending." sections={sections} related={related}/>;
}
