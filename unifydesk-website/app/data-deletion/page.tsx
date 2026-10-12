import type { Metadata } from "next";
import LegalDraftPage from "../legal-draft-page";
export const metadata: Metadata = {
  title: "Data Deletion Instructions",
  description: "Draft India Early Access Data Deletion Instructions for UnifyDesk",
  alternates: { canonical: "/data-deletion" },
  robots: { index: false, follow: false },
};
const sections = [
  {
    "title": "How to start a request",
    "text": [
      "Authenticated users can request account deletion through available account controls. Authorized workspace administrators can request company deletion. For data uploaded by a business customer, contact that business first when appropriate.",
      "Data-deletion requests can be sent to support@unifydesk.in. Verify inbox delivery and the authenticated deletion workflow before submitting this URL to Meta; a recipient must not need a UnifyDesk account merely to ask for deletion."
    ]
  },
  {
    "title": "User account deletion",
    "text": [
      "The user-deletion backend checks ownership and current password, revokes access, marks the user DELETION_PENDING and schedules background purge work. Users who own workspaces must transfer or delete those workspaces first."
    ]
  },
  {
    "title": "Company workspace deletion",
    "text": [
      "The company-deletion backend disconnects channels, initiates cancellation of associated billing arrangements, marks a workspace DELETION_PENDING and schedules staged purge work. It can include related inbox, company access, contact and media records."
    ]
  },
  {
    "title": "Disconnecting a provider",
    "text": [
      "Provider-specific disconnect may revoke credentials or remove a channel connection, but it is not necessarily the same as deleting all content already synchronized into UnifyDesk. Google, Microsoft, Zoho and Meta can also retain information under their own policies."
    ]
  },
  {
    "title": "Contact deletion and suppression",
    "text": [
      "Contacts can be removed while keyed suppression fingerprints may be retained for opt-out and complaint protection, preventing re-import from silently restoring eligibility."
    ]
  },
  {
    "title": "Completion, exceptions and verification",
    "text": [
      "Backend deletion uses a configurable default 30-day grace period and retrying purge jobs. No guaranteed universal completion date, backup expiry or full provider erasure is established.",
      "Before publication, test database records, linked media, attachments, logs, backups, exports, invoices, provider-side revocation, retries and the exact deployed retention settings. Define identity verification, acknowledgements and permitted legal exceptions."
    ]
  }
];
const related = [{"label":"Privacy","href":"/privacy"},{"label":"Subprocessors","href":"/subprocessors"}];
export default function Page(){
  return <LegalDraftPage title="Data Deletion Instructions" intro="Draft deletion guidance for end users, workspace administrators and recipients of customer messages." sections={sections} related={related}/>;
}
