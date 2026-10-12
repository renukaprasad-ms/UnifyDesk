import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Data Processing Addendum",
  description: "Draft India Early Access Data Processing Addendum for UnifyDesk",
  alternates: { canonical: "/dpa" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Parties and roles",
    "text": [
      "Insert the verified UnifyDesk legal proprietor identity and the customer's legal identity; identify responsibility for account, billing and customer-controlled content. Do not assign statutory data-processing roles without counsel review."
    ]
  },
  {
    "title": "Instructions and scope",
    "text": [
      "Subject matter may include contact records, email/WhatsApp conversations, attachments, credentials, campaigns, opt-out evidence and operations necessary to supply enabled services."
    ]
  },
  {
    "title": "Security and suppliers",
    "text": [
      "Attach an audited security schedule and confirmed list of vendors, controls, data locations and documented instructions. Do not assert certification or service levels without evidence."
    ]
  },
  {
    "title": "Assistance, termination and legal terms",
    "text": [
      "Define assistance with rights requests, security incidents, deletion and return, retention exceptions, subcontractors, liability, governing law and signature/acceptance rules after actual operational validation."
    ]
  }
];
const related = [{"label":"Privacy","href":"/privacy"},{"label":"Subprocessors","href":"/subprocessors"},{"label":"Terms","href":"/terms"}];
export default function Page(){
  return <LegalDraftPage title="Data Processing Addendum" intro="Non-binding negotiation draft for business-customer processing terms." sections={sections} related={related}/>;
}
