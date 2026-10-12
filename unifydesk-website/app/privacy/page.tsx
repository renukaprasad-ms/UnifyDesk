import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Privacy Policy",
  description: "Draft India Early Access Privacy Policy for UnifyDesk",
  alternates: { canonical: "/privacy" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "Provider identity and requests",
    "text": [
      "UnifyDesk is being established as an Indian sole proprietorship. Before publication, insert the exact legal proprietor identity and verifiable legal correspondence address from approved business records.",
      "Verified public privacy/grievance address: [CONFIRM PRIVACY CONTACT EMAIL]. Verified postal address: [CONFIRM BUSINESS NOTICE ADDRESS]. These are unresolved release blockers, not contact channels."
    ]
  },
  {
    "title": "Information handled",
    "text": [
      "We may process names, work email addresses, login and session records, workspace membership, role assignments, subscription details, technical diagnostics and support requests.",
      "Customer-controlled data can include imported contacts and phone numbers, opt-in/opt-out records, message headers (including To/CC/BCC), subjects and bodies, attachments, campaign audience and delivery status, WhatsApp metadata and advertising account data."
    ]
  },
  {
    "title": "Our role and customer responsibility",
    "text": [
      "We operate account, security, website, service and billing workflows for UnifyDesk. Business customers choose whose contact, message and campaign data to submit and which providers to connect; those customers remain responsible for appropriate permissions, required notices and lawful outreach.",
      "Different processing activities may have different legal roles. The roles and instructions for business-customer content must be finalized in the Terms and a customer Data Processing Addendum."
    ]
  },
  {
    "title": "Google, Microsoft, Zoho and Meta integrations",
    "text": [
      "The reviewed backend supports Gmail, Microsoft and Zoho inbox connections and Meta/WhatsApp and advertising integrations. Where authorized, it can synchronize email bodies, recipients, message metadata and attachment references, send messages and maintain provider connection state. Some attachments are streamed from the provider; other objects may use configured media storage.",
      "Default code scopes include Gmail gmail.modify; Microsoft Mail.ReadWrite and Mail.Send; and ZohoMail.messages.ALL. Production scopes and all data transfers need verification.",
      "Google API information must be handled under the Google API Services User Data Policy and applicable Limited Use requirements. UnifyDesk must audit these purposes and flows before submitting Google app verification."
    ]
  },
  {
    "title": "Why information is used",
    "text": [
      "We use information to authenticate users, enable requested workplace features, deliver communications through selected providers, manage contacts and consent, process legitimate billing, operate support, monitor security and investigate misuse.",
      "Storing a contact does not itself establish consent to receive marketing. The backend includes channel-specific preference and suppression logic."
    ]
  },
  {
    "title": "Disclosure to vendors",
    "text": [
      "Depending on which services are enabled, information may be shared with customer-authorized Google, Microsoft, Zoho and Meta integrations; messaging providers; configured hosting/object storage; payment processing; and website analytics.",
      "The exact legal vendor entities, environments, data locations and international transfers are not confirmed; review the Subprocessors inventory before publishing."
    ]
  },
  {
    "title": "Analytics and cookies",
    "text": [
      "The marketing site's reviewed Next.js layout loads Google Analytics 4 after page interaction begins. No consent gate is visible in that file. Actual tracking, cookies, geographic access and applicable choice mechanisms must be checked before launch."
    ]
  },
  {
    "title": "Security and retention",
    "text": [
      "The reviewed code has role/company permission models, stored encrypted provider credential payloads, authorized attachment access, and deletion-processing services. This is not a certification that each control is deployed or independently audited.",
      "Account and workspace deletion are staged; the code includes a configurable default 30-day delayed purge, but this is not a universal retention promise. Notification records, imports, media, backups, security logs and financial records may have other timelines or exceptions. Some deleted contacts retain protected suppression fingerprints."
    ]
  },
  {
    "title": "Rights, complaints and changes",
    "text": [
      "Requests should be authenticated and may need to be directed to the business customer controlling the recipient information. Publish an actual working email or public request form before approval; do not require a public data subject to create an account solely to ask for deletion.",
      "A final notice must explain the rights and grievance procedures required by applicable Indian law according to its current phased commencement schedule, and give a real effective date."
    ]
  }
];
const related = [{"label":"Data Deletion","href":"/data-deletion"},{"label":"Cookies","href":"/cookies"},{"label":"Subprocessors","href":"/subprocessors"},{"label":"Terms","href":"/terms"}];
export default function Page(){
  return <LegalDraftPage title="Privacy Policy" intro="Proposed notice for India Early Access covering the UnifyDesk website, application and customer-authorized integrations. All disclosures require final operational and legal approval." sections={sections} related={related}/>;
}
